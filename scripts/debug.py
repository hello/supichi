import os
import argparse
import base64

import boto3
from boto3.dynamodb.conditions import Key, Attr

def debug(args):
    account_id = args.account

    ddb = boto3.resource('dynamodb',
        aws_access_key_id=args.key,
        aws_secret_access_key=args.secret)

    speech_timeline = ddb.Table('speech_timeline')
    speech_results = ddb.Table('speech_results')

    kms = boto3.client('kms',
        aws_access_key_id=args.key,
        aws_secret_access_key=args.secret,
        region_name="us-east-1")

    s3 = boto3.client('s3',
        aws_access_key_id=args.key,
        aws_secret_access_key=args.secret,
        region_name="us-east-1")

    # get speech timeline
    response = speech_timeline.query(
        KeyConditionExpression=Key('account_id').eq(account_id),
        Limit=args.limit,
        ScanIndexForward=False)

    # for debug
    # boto3.set_stream_logger(name='botocore')

    speeches = response['Items']
    results = {'uuids': [], 'ts':[], 'transcript': []}
    for speech in speeches:
        euuid = speech['e_uuid']

        decrypted = kms.decrypt(
            CiphertextBlob=base64.b64decode(euuid),
            EncryptionContext={'account_id': str(account_id)})

        uuid = decrypted['Plaintext']

        # get transription results
        res = speech_results.get_item(Key={'uuid' : uuid})
        res2 = {}
        if 'Item' in res:
            res2 = res['Item']

        results['uuids'].append(uuid)
        results['ts'].append(speech['ts'])
        results['transcript'].append(res2)


    # results and download audio file
    index = 0
    for uuid in results['uuids']:
        ts = results['ts'][index]
        confidence = 0.0
        text = ""
        if results['transcript'][index] and 'conf' in results['transcript'][index]:
            confidence = results['transcript'][index]['conf']
            text = results['transcript'][index]['text']
            
        print "ts=%s id=%s confidence=%0.4f transcript=%s" % (
            ts, uuid, confidence, text)

        index += 1

        if args.download:
            exist = os.path.isdir("account_%s" % account_id)
            if not exist:
                os.mkdir("account_%s" % account_id)

            filename = "%s.raw" % (uuid)
            with open("account_%s/%s" %(account_id, filename), "wb") as f:
                s3.download_fileobj(
                    "hello-voice-upload-dev", "sense_1_5/%s" % filename, f)
                

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

    os.system("aws configure set region us-east-1")
    os.system("aws configure set default.s3.signature_version s3v4")
    args = parse_args()
    debug(args)

