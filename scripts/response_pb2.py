# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: response.proto

from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import descriptor_pb2
# @@protoc_insertion_point(imports)




DESCRIPTOR = _descriptor.FileDescriptor(
  name='response.proto',
  package='',
  serialized_pb='\n\x0eresponse.proto\"\xaa\x01\n\x0eSpeechResponse\x12\x0b\n\x03url\x18\x01 \x01(\t\x12\x0c\n\x04text\x18\x02 \x01(\t\x12&\n\x06result\x18\x03 \x01(\x0e\x32\x16.SpeechResponse.Result\x12\x19\n\x11\x61udio_stream_size\x18\x04 \x01(\r\":\n\x06Result\x12\x06\n\x02OK\x10\x00\x12\x0b\n\x07UNKNOWN\x10\x01\x12\x0c\n\x08REJECTED\x10\x02\x12\r\n\tTRY_AGAIN\x10\x03\x42\x15\n\x13is.hello.speech.api')



_SPEECHRESPONSE_RESULT = _descriptor.EnumDescriptor(
  name='Result',
  full_name='SpeechResponse.Result',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='OK', index=0, number=0,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UNKNOWN', index=1, number=1,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='REJECTED', index=2, number=2,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='TRY_AGAIN', index=3, number=3,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=131,
  serialized_end=189,
)


_SPEECHRESPONSE = _descriptor.Descriptor(
  name='SpeechResponse',
  full_name='SpeechResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='url', full_name='SpeechResponse.url', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=unicode("", "utf-8"),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='text', full_name='SpeechResponse.text', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=unicode("", "utf-8"),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='result', full_name='SpeechResponse.result', index=2,
      number=3, type=14, cpp_type=8, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='audio_stream_size', full_name='SpeechResponse.audio_stream_size', index=3,
      number=4, type=13, cpp_type=3, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
    _SPEECHRESPONSE_RESULT,
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  serialized_start=19,
  serialized_end=189,
)

_SPEECHRESPONSE.fields_by_name['result'].enum_type = _SPEECHRESPONSE_RESULT
_SPEECHRESPONSE_RESULT.containing_type = _SPEECHRESPONSE;
DESCRIPTOR.message_types_by_name['SpeechResponse'] = _SPEECHRESPONSE

class SpeechResponse(_message.Message):
  __metaclass__ = _reflection.GeneratedProtocolMessageType
  DESCRIPTOR = _SPEECHRESPONSE

  # @@protoc_insertion_point(class_scope:SpeechResponse)


DESCRIPTOR.has_options = True
DESCRIPTOR._options = _descriptor._ParseOptions(descriptor_pb2.FileOptions(), '\n\023is.hello.speech.api')
# @@protoc_insertion_point(module_scope)
