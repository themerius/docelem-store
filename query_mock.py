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


# pip install stompest==2.1.6
from stompest.config import StompConfig
from stompest.sync import Stomp


user = os.getenv('APOLLO_USER') or 'admin'
password = os.getenv('APOLLO_PASSWORD') or 'password'
host = os.getenv('APOLLO_HOST') or 'ashburner'
port = int(os.getenv('APOLLO_PORT') or 61613)
destination = sys.argv[1:2] or ['/queue/docelem-store.dev']
destination = destination[0]

config = StompConfig('tcp://%s:%d' % (host, port), login=user, passcode=password, version='1.1')
client = Stomp(config)
client.connect(host=host)

clientConsumer = Stomp(config)
clientConsumer.connect(host=host)

count = 0
start = time.time()

def signal_handler(signal, frame):
    print('You pressed Ctrl+C!')
    diff = time.time() - start
    print 'Received %s frames in %f seconds' % (count, diff)
    client.disconnect(receipt='bye')
    client.receiveFrame()
    client.close()
    clientConsumer.disconnect(receipt='bye')
    clientConsumer.receiveFrame()
    clientConsumer.close()
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

# Send a query
# body = "scai.fhg.de/abstract/121212"
# headers = {
#     'transformation': 'TEXT',
#     'event': 'QueryDocelem',
#     'reply-to': '/topic/docstore-reply'
# }

body = "scai.fhg.de/prominer-entry/hs00001;scai.fhg.de/prominer-entry/hs00002"
headers = {
    'transformation': 'TEXT',
    'event': 'QueryAnnotationIndex',
    'reply-to': '/topic/docstore-reply'
}

print "Sending to " + destination
client.send(destination=destination, body=body, headers=headers)

clientConsumer.subscribe(destination='/topic/docstore-reply', headers={'id': 'required-for-STOMP-1.1'})

while True:
    startReceive = time.time()
    frame = clientConsumer.receiveFrame()
    diffReceive = time.time() - startReceive
    print frame
    print diffReceive
    time.sleep(1)
    print "Sending to " + destination
    client.send(destination=destination, body=body, headers=headers)
