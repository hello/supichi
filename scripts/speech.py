import requests
import time
import os
import sys
import struct
from StringIO import StringIO

import traceback
import response_pb2
import json

# construct protobuf
import speech_pb2
import struct

class SlowUpload(object):
  def __init__(self, fp, verbose=False):
    self.f = open(fp, 'rb')
    self.size = 2048 
    self.sent = 0
    self.verbose = verbose

    self.f.seek(0, os.SEEK_END)
    self.total_size = self.f.tell()
    self.f.seek(0)
    print "total_size", self.total_size

  # def read(self, size):
  #   print "sleeping"
  #   time.sleep(1)
  #   print "reading"
  #   return self.f.read(size)

  def print_line(self, message=''):
    if self.verbose:
      print message

  def next(self):
    if self.sent >= self.total_size:
      raise StopIteration
    #time.sleep(0.1)
    self.print_line("reading")

    res = self.f.read(self.size)
    self.sent += len(res)
    return res
    
  def __iter__(self):
    return self


def decode_varint(value):
    """Decodes a single Python integer from a VARINT.
    Note that `value` may be a stream containing more than a single
    encoded VARINT. Only the first VARINT will be decoded and returned. If
    you expect to be handling multiple VARINTs in a stream you might want to
    use the `decode_varint_stream` function directly.
    """
    return decode_varint_stream(value).next()


def decode_varint_stream(stream):
    """Lazily decodes a stream of VARINTs to Python integers."""
    value = 0
    base = 1
    num_bytes = 0;
    for raw_byte in stream:
        val_byte = ord(raw_byte)
        value += (val_byte & 0x7f) * base
        num_bytes += 1
        if (val_byte & 0x80):
            # The MSB was set; increase the base and iterate again, continuing
            # to calculate the value.
            base *= 128
        else:
            # The MSB was not set; this was the last byte in the value.
            yield value, num_bytes
            value = 0
            base = 1

def get_message(body, msgtype):
    """ Read a message from a socket. msgtype is a subclass of
        of protobuf Message.
    """

    msg_len = struct.unpack('>I', body[:4])[0] # + '\x00\x00\x00')[0]
    num_bytes = 4

    # varint
    # msg_len, num_bytes = decode_varint(body);
    # print "msg_len:", msg_len
    # print "num_bytes:", num_bytes

    pb_start = num_bytes
    pb_end = num_bytes + msg_len

    msg_buf = body[pb_start : pb_end]

    msg = response_pb2.SpeechResponse()
    try:
        msg.ParseFromString(msg_buf)
    except Exception as e:
        print "Protobuf parsing error"
        traceback.print_exc(file=sys.stdout)

    audio_data = body[pb_end:]
    fp = open('./tmp.wav', 'wb')
    fp.write(audio_data)
    fp.close()

    return msg

if __name__ == '__main__':
    filename = sys.argv[1]
    sampling_rate = sys.argv[2]
    env = sys.argv[3]
    audio_type = sys.argv[4]
    eq = sys.argv[5]

    if audio_type not in ['mp3', 'adpcm']:
        print "audio type needs to be mp3 or adpcm"
        sys.exit()

    pb = "false"

    if env != 'prod':
        aes_key = "CD0C57B4B5C69D4C28F75AC4FBA5FF22".decode("hex"); # for 8AF6441AF72321F4
    else:
        aes_key = "729FD5B0ADCC7AFF2173D5406FC0AB5C".decode("hex"); # for 9ECA262C40A3E894

    eq_options = {
        "sense_one" : speech_pb2.SENSE_ONE,
        "none" : speech_pb2.NONE
    }

    audio_options = {
        "mp3" : speech_pb2.MP3,
        "adpcm" : speech_pb2.ADPCM
    }

    # read audio data
    fp = open(filename, 'rb')
    file_data = fp.read();
    fp.close()

    
    speech_data = speech_pb2.SpeechRequest()
    speech_data.word = speech_pb2.OK_SENSE
    speech_data.version = 1
    speech_data.confidence = 125
    speech_data.eq = eq_options.get(eq, speech_pb2.NONE)
    speech_data.response = audio_options.get(audio_type, speech_pb2.MP3)
    pb_str = speech_data.SerializeToString()

    print "speech_data:", speech_data
    print "protobuf msg:", pb_str
    print "protobuf size: ", len(pb_str)

    # add protobuf to payload
    pb_size = struct.pack('>I', len(pb_str))
    file_data = pb_size + pb_str + file_data;

    # create HMAC
    import hmac
    import hashlib
    hashed = hmac.new(aes_key, file_data, hashlib.sha1)
    print "length of hash", len(hashed.digest())

    su = file_data + hashed.digest()

    # check protobuf again
    speech_data2 = speech_pb2.SpeechRequest()
    speech_data2.ParseFromString(pb_str)
    print "Parsed", speech_data2

    headers = {"content-type": "application/octet-stream",
            "X-Hello-Sense-Id": "8AF6441AF72321F4"}

    if env == 'localtext':
        text = sys.argv[6]
        su = json.dumps({'sense_id': '721E040D184F2CAE', 'transcript': text})
        headers = {"content-type": "application/json", "X-Hello-Sense-Id": "721E040D184F2CAE"}
        ENDPOINT = "http://localhost:8181/upload/text?response=%s" % (audio_type)

    elif env == 'local':
        ENDPOINT = "http://localhost:9999/v2/upload/audio"
        ENDPOINT = "http://localhost:8181/v2/upload/audio"

    # dev endpoints
    elif env == 'dev':
        ENDPOINT = "https://dev-speech.hello.is/v2/upload/audio"
    # prod
    elif env == "prod":
        ENDPOINT = "https://speech.hello.is/v2/upload/audio"
        headers = {"content-type": "application/octet-stream", "X-Hello-Sense-Id": "9ECA262C40A3E894"}
    else:
        print "invalid env. choose from [local/dev/goog]"
        sys.exit(1)
    
    print "Endpoint: %s" % ENDPOINT

    t1 = time.time()
    r = requests.post(ENDPOINT, data=su, headers=headers)
    t2 = time.time()
    if r.status_code == 200:
        print "success", r.status_code
    else:
      print "Failed", r.status_code


    for k,v in r.headers.items():
        print "head", k, v

    print "file: %s, time: %f" % (sys.argv[1],  (t2-t1))
    print "response length: %d" % len(r.content)

    if pb == 'true':
        msg = get_message(r.content, response_pb2.SpeechResponse)
        print "protobuf:\n", msg
    else:
        fname = "./tmp1-eq-%s.%s" % (eq, audio_type)
        with open(fname, 'wb') as fp:
            fp.write(r.content)
