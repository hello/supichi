import requests
import time
import os
import sys
import struct
from StringIO import StringIO

import traceback
import response_pb2

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

    if audio_type not in ['mp3', 'adpcm']:
        print "audio type needs to be mp3 or adpcm"
        sys.exit()

    pb = "false"
    if len(sys.argv) == 6:
        pb = sys.argv[5]

    # su = SlowUpload(filename)
    # testing 8AF6441AF72321F4 2095
    # demo C8DAAC353AEFA4A9 62297
    import hmac
    import hashlib
    import base64

    aes_key = "CD0C57B4B5C69D4C28F75AC4FBA5FF22".decode("hex"); # for 8AF6441AF72321F4
    fp = open(filename, 'rb')
    file_data = fp.read();
    hashed = hmac.new(aes_key, file_data, hashlib.sha1)
    print "length of hash", len(hashed.digest())
    su = file_data + hashed.digest()

    headers = {"content-type": "application/octet-stream", "X-Hello-Sense-Id": "8AF6441AF72321F4"}
    if env == 'local':
        ENDPOINT = "http://localhost:8181/v1/upload/audio?r=%s&pb=%s&response=%s" % (sampling_rate, pb, audio_type)
    elif env == 'dev':
        ENDPOINT = "http://dev-speech.hello.is/v1/upload/audio?r=%s&response=%s" % (sampling_rate, audio_type)
    elif env == 'goog':
        ENDPOINT = "http://8.34.219.91:8181/upload/audio?r=%s" % (sampling_rate)
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
        if audio_type == "mp3":
            fp = open('./tmp1.mp3', 'wb')
        else:
            fp = open('./tmp1.wav', 'wb')
        fp.write(r.content)
        fp.close()



