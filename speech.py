import requests
import time
import os
import sys
import struct
from StringIO import StringIO

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



def get_message(body, msgtype):
    """ Read a message from a socket. msgtype is a subclass of
        of protobuf Message.
    """
    a = bytearray(body)
    msg_len = struct.unpack('>L', a)[0]
    print "msg_len", msg_len
    msg_buf = body[4:]

    msg = msgtype()
    msg.ParseFromString(msg_buf)
    return msg

if __name__ == '__main__':
    filename = sys.argv[1]
    sampling_rate = sys.argv[2]
    env = sys.argv[3]

    su = SlowUpload(filename)
    headers = {"content-type": "application/octet-stream"} #, "X-Hello-Sense-Id": "8AF6441AF72321F4"}
    if env == 'local':
        ENDPOINT = "http://localhost:8181/upload/pb?r=%s" % (sampling_rate)
    elif env == 'dev':
        ENDPOINT = "http://dev-speech.hello.is/upload/pb?r=%s" % (sampling_rate)
    elif env == 'goog':
        ENDPOINT = "http://8.34.219.91:8181/upload/pb?r=%s" % (sampling_rate)
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

    print "file: %s, time: %f, text: %s" % (sys.argv[1],  (t2-t1), r.text)

