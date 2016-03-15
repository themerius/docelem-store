#!/usr/bin/env python
"""
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
import os
import sys
import time
import signal
import xml.etree.ElementTree as ElementTree


# pip install stompest==2.1.6
from stompest.config import StompConfig
from stompest.sync import Stomp

# Timeout Decorator
from functools import wraps
import errno
import os
import signal


def timeout(seconds=10, error_message=os.strerror(errno.ETIME)):
    def decorator(func):
        def _handle_timeout(signum, frame):
            sys.exit(0)

        def wrapper(*args, **kwargs):
            signal.signal(signal.SIGALRM, _handle_timeout)
            signal.alarm(seconds)
            try:
                result = func(*args, **kwargs)
            finally:
                signal.alarm(0)
            return result

        return wraps(func)(wrapper)

    return decorator


user = os.getenv('APOLLO_USER') or 'admin'
password = os.getenv('APOLLO_PASSWORD') or 'password'
host = os.getenv('APOLLO_HOST') or 'ashburner'
port = int(os.getenv('APOLLO_PORT') or 61613)
destination = sys.argv[1:2] or ['/queue/docelem-store']
destination = destination[0]

config = StompConfig('tcp://%s:%d' % (host, port), login=user, passcode=password, version='1.1')
client = Stomp(config)
client.connect(host=host)

clientConsumer = Stomp(config)
clientConsumer.connect(host=host)

# Subscribe to the topic where the answers are posted to
clientConsumer.subscribe(destination='/topic/docstore-reply', headers={'id': 'required-for-STOMP-1.1'})

# Send a query
queries = range(1,15)
headers = {
    'transformation': 'TEXT',
    'event': 'QueryDocelem',
    'reply-to': '/topic/docstore-reply'
}

client.send(destination=destination, body="scai.fhg.de/voc/ccg", headers=headers)

for i in queries:
    client.send(destination=destination, body="scai.fhg.de/concept/" + str(i), headers=headers)

print u"# ErasmusMC ontology file".encode('utf-8')
print u"VR 0.0".encode('utf-8')
print u"ON CCG_Ontology".encode('utf-8')
print u"--".encode('utf-8')

@timeout(1)
def receiveFrames():
    frame = clientConsumer.receiveFrame()

    xml = ElementTree.fromstring(frame.body)
    modelText = xml.find('docelem').find('model').text
    if (modelText is not None):
        print unicode(modelText).encode('utf-8')
        print u"--".encode('utf-8')

for reply in queries:
    receiveFrames()

client.disconnect(receipt='bye')
client.receiveFrame()
client.close()
clientConsumer.disconnect(receipt='bye')
clientConsumer.receiveFrame()
clientConsumer.close()
