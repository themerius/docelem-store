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
destination = sys.argv[1:2] or ['/queue/docelem-store']
destination = destination[0]

config = StompConfig('tcp://%s:%d' % (host, port), login=user, passcode=password, version='1.1')
client = Stomp(config)
client.connect(host=host)

count = 0
start = time.time()

def signal_handler(signal, frame):
    print('You pressed Ctrl+C!')
    diff = time.time() - start
    print 'Received %s frames in %f seconds' % (count, diff)
    client.disconnect(receipt='bye')
    client.receiveFrame()
    client.close()
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

while True:
    time.sleep(1)  # delay in secs
    body = []
    body.append("""
    <corpus>
      <docelems>
        <docelem>
          <uiid>scai.fhg.de/abstract/121212</uiid>
          <model>Abstract's text</model>
        </docelem>
        <docelem>
          <uiid>scai.fhg.de/abstract/131313</uiid>
          <model>Other abstract's text</model>
        </docelem>
        <docelem>
          <uiid>scai.fhg.de/abstract/141414</uiid>
          <model>Abstract's text</model>
        </docelem>
        <docelem>
          <uiid>scai.fhg.de/abstract/151515</uiid>
          <model>Other abstract's text</model>
        </docelem>
      </docelems>
      <annotations>test annot</annotations>
    </corpus>
    """)
    print "Sending to " + destination
    client.send(destination=destination, body=body[0], headers={'transformation': 'TEXT', 'event': 'FoundCorpus'})
    count = count + 1
