import argparse

import boto3
from boto3.dynamodb.conditions import Key, Attr

import base64

import os


BUCKET = 'hello-voice-upload-dev'
ACCESS_KEY = 'AKIAJ5F7MY4477II5GTA'
SECRET_KEY ='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'
REGION = 'us-east-1'

def debug(args):
    account_id = args.account
    ACCESS_KEY = args.key
    SECRET_KEY = args.secret

    dynamodb = boto3.resource('dynamodb', 
        aws_access_key_id=ACCESS_KEY,
        aws_secret_access_key=SECRET_KEY)

    speech_timeline = dynamodb.Table('speech_timeline')

    speech_results = dynamodb.Table('speech_results')

    kms = boto3.client('kms',
        aws_access_key_id=ACCESS_KEY, aws_secret_access_key=SECRET_KEY)


    # get timeline
    response = speech_timeline.query(
        KeyConditionExpression=Key('account_id').eq(account_id),
        Limit=args.limit,
        ScanIndexForward=False)

    # for debug
    # boto3.set_stream_logger(name='botocore')

    speeches = response['Items']
    results = {'uuids': [], 'ts':[], 'result': []}
    for speech in speeches:
        euuid = speech['e_uuid']

        decrypted = kms.decrypt(
            CiphertextBlob=base64.b64decode(euuid),
            EncryptionContext={'account_id': str(account_id)})

        uuid = decrypted['Plaintext']

        res = speech_results.get_item(Key={'uuid' : uuid})
        res2 = {}
        if 'Item' in res:
            res2 = res['Item']

        # print account_id, speech['ts'], uuid
        results['uuids'].append(uuid)
        results['ts'].append(speech['ts'])
        results['result'].append(res2)


    s3 = boto3.client('s3',
        aws_access_key_id=ACCESS_KEY, aws_secret_access_key=SECRET_KEY)
        # host='https://s3.dualstack.us-east-1.amazonaws.com')

    index = 0
    for uuid in results['uuids']:
        ts = results['ts'][index]
        confidence = 0.0
        text = ""
        if results['result'][index] and 'conf' in results['result'][index]:
            confidence = results['result'][index]['conf']
            text = results['result'][index]['text']
            
        print "ts=%s id=%s confidence=%0.4f transcript=%s" % (
            ts, uuid, confidence, text)

        index += 1

        if args.download:
            filename = "%s.raw" % (uuid)
            with open(filename, "wb") as f:
                s3.download_fileobj(BUCKET, "sense_1_5/%s" % filename, f)
                

def parse_args():
    parser = argparse.ArgumentParser(description='debug supichi')
    parser.add_argument('--key', type=str, help='aws_access_key')
    parser.add_argument('--secret', type=str, help='aws_secret')
    parser.add_argument('--account', type=int, help='account-id')
    parser.add_argument('--limit', type=int, default=10, help='items to get (default 10)')
    parser.add_argument('--download', type=bool, default=False, help='download audio True/False')
    args = parser.parse_args()
    return args

if __name__ == '__main__':

    os.system("aws configure set default.s3.signature_version s3v4")
    args = parse_args()
    debug(args)

