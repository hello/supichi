# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: speech.proto

from google.protobuf.internal import enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import descriptor_pb2
# @@protoc_insertion_point(imports)




DESCRIPTOR = _descriptor.FileDescriptor(
  name='speech.proto',
  package='',
  serialized_pb='\n\x0cspeech.proto\"9\n\x0bspeech_data\x12\x16\n\x04word\x18\x01 \x01(\x0e\x32\x08.keyword\x12\x12\n\nconfidence\x18\x02 \x01(\x05*B\n\x07keyword\x12\x08\n\x04NULL\x10\x00\x12\x0c\n\x08OK_SENSE\x10\x01\x12\x08\n\x04STOP\x10\x02\x12\n\n\x06SNOOZE\x10\x03\x12\t\n\x05\x41LEXA\x10\x04')

_KEYWORD = _descriptor.EnumDescriptor(
  name='keyword',
  full_name='keyword',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='NULL', index=0, number=0,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='OK_SENSE', index=1, number=1,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='STOP', index=2, number=2,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='SNOOZE', index=3, number=3,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='ALEXA', index=4, number=4,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=75,
  serialized_end=141,
)

keyword = enum_type_wrapper.EnumTypeWrapper(_KEYWORD)
NULL = 0
OK_SENSE = 1
STOP = 2
SNOOZE = 3
ALEXA = 4



_SPEECH_DATA = _descriptor.Descriptor(
  name='speech_data',
  full_name='speech_data',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='word', full_name='speech_data.word', index=0,
      number=1, type=14, cpp_type=8, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='confidence', full_name='speech_data.confidence', index=1,
      number=2, type=5, cpp_type=1, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  serialized_start=16,
  serialized_end=73,
)

_SPEECH_DATA.fields_by_name['word'].enum_type = _KEYWORD
DESCRIPTOR.message_types_by_name['speech_data'] = _SPEECH_DATA

class speech_data(_message.Message):
  __metaclass__ = _reflection.GeneratedProtocolMessageType
  DESCRIPTOR = _SPEECH_DATA

  # @@protoc_insertion_point(class_scope:speech_data)


# @@protoc_insertion_point(module_scope)
