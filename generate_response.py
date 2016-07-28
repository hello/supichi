#-*- coding: utf-8 -*-
import requests
import time
import os
import sys

import json


VALID_SENSORS = ['TEMPERATURE', 'HUMIDITY', 'LIGHT', 'PARTICULATES', 'SOUND', 'ERRORS']
def post_request(env, body):
    headers = {"content-type": "application/json"}
    url = "http://dev-speech.hello.is"
    if env == 'local':
        url = "http://localhost:8181"

    endpoint = "%s/queue/send_message/voice_response" % (url) 
    r = requests.post(endpoint, data=json.dumps(body), headers=headers)

    text = body["text"].decode('utf-8', 'ignore')
    if r.status_code == 200:
        print "%s -- success -- %s" % (text, r.text)
    else:
        print "%s -- failure -- %s" % (text, r.text)

def process_temperature(env, value, body):
    body["text"] = "It's currently %s degrees" % (value)
    body["parameters"] = "value_%s_F-opt_1" % (value)
    r = post_request(env, body)

    body["text"] = "It's %s degrees" % (value)
    body["parameters"] = "value_%s_F-opt_2" % (value)
    r = post_request(env, body)

    body["text"] = "It's currently %s ºF" % (value)
    body["parameters"] = "value_%s_F-opt_3" % (value)
    r = post_request(env, body)

    body["text"] = "It's currently %s ºF" % (value)
    body["parameters"] = "value_%s_F-opt_4" % (value)
    r = post_request(env, body)


def process_humidity(env, value, body):
    body["text"] = "The humidity is currently %s percent" % (value)
    body["parameters"] = "value_%s-opt_1" % (value)
    r = post_request(env, body)

    body["text"] = "It's currently %s percent" % (value)
    body["parameters"] = "value_%s-opt_2" % (value)
    r = post_request(env, body)


def process_light(env, value, body):
    body["text"] = "It's currently %s lux" % (value)
    body["parameters"] = "value_%s-opt_1" % (value)
    r = post_request(env, body)

    body["text"] = "It's currently %s lux in your room" % (value)
    body["parameters"] = "value_%s-opt_2" % (value)
    r = post_request(env, body)
 
def process_sound(env, value, body):
    body["text"] = "It's currently %s decibels" % (value)
    body["parameters"] = "value_%s-opt_1" % (value)
    r = post_request(env, body)

    body["text"] = "It's currently %s d b" % (value)
    body["parameters"] = "value_%s-opt_2" % (value)
    r = post_request(env, body)
 
def process_air(env, value, body):
    body["text"] = "The air quality in your room is %s micrograms per cubic meter" % (value)
    body["parameters"] = "value_%s-opt_1" % (value)
    r = post_request(env, body)

    body["text"] = "It's currently %s micrograms per cubic meter" % (value)
    body["parameters"] = "value_%s-opt_2" % (value)
    r = post_request(env, body)

def process_error_msgs(env, body):

    body["text"] = "Sorry. I wasn't able to access your air quality data right now. Please try again later."
    body["parameters"] = "no_data"
    body["category"] = "PARTICULATES"
    r = post_request(env, body)
    
    body["text"] = "Sorry. I wasn't able to access your temperature data right now. Please try again later."
    body["parameters"] = "no_data"
    body["category"] = "TEMPERATURE"
    r = post_request(env, body)

    body["text"] = "Sorry. I wasn't able to access your humidity data right now. Please try again later."
    body["parameters"] = "no_data"
    body["category"] = "HUMIDITY"
    r = post_request(env, body)

    body["text"] = "Sorry. I wasn't able to access your light data right now. Please try again later."
    body["parameters"] = "no_data"
    body["category"] = "LIGHT"
    r = post_request(env, body)

    body["text"] = "Sorry. I wasn't able to access your sound data right now. Please try again later."
    body["parameters"] = "no_data"
    body["category"] = "SOUND"
    r = post_request(env, body)

if __name__ == '__main__':

    sensor = sys.argv[1].upper()
    if sensor not in VALID_SENSORS:
        print VALID_SENSORS
        sys.exit(1)

    env = sys.argv[2]
    if env not in ['local', 'dev']:
        print "wrong env"
        sys.exit(1)

    body = {
        "text":"",
        "intent":"ROOM_CONDITIONS",
        "action":"GET_SENSOR",
        "category": sensor,
        "service_type":"WATSON",
        "voice_type":"ALLISON",
        "response_type":"SUCCESS",
        "parameters":""
    }

    if sensor == 'ERRORS':
        process_error_msgs(env, body)
        sys.exit();

    low = 1
    high = 101
    if sensor == 'LIGHT':
        high = 300
    elif sensor == 'PARTICULATES':
        low = 100
        high = 301

    for i in range (low, high):
        value = str(i)

        if sensor == 'TEMPERATURE':
            process_temperature(env, value, body)
        elif sensor == 'HUMIDITY':
            process_humidity(env, value, body)
        elif sensor == 'LIGHT':
            process_light(env, value, body)
        elif sensor == 'SOUND':
            process_sound(env, value, body)
        elif sensor == 'PARTICULATES':
            process_air(env, value, body)

    # additional
    if sensor == "LIGHT":
        body["text"] = "It's currently under 1 lux"
        body["parameters"] = "under_1_lux-opt_1"
        r = post_request(env, body)
