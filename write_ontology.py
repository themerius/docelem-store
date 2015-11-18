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

body = """
<corpus>
  <docelems>
    <docelem>
      <uiid>scai.fhg.de/voc/ccg</uiid>
      <model>NS Voc\nID CCG\nNA Cologne Center for Genomics</model>
    </docelem>
    <docelem>
      <uiid>scai.fhg.de/semtype/1000</uiid>
      <model>NS SemType\nID 1000\nNA chemical compound\nDF A chemical compound (or just compound if used in the context of chemistry) is an entity consisting of two or more different atoms which associate via chemical bonds.</model>
    </docelem>
    <docelem>
      <uiid>scai.fhg.de/concept/1</uiid>
      <model>ID 1\nNA Ceritinib\nTM Ceritinib\nTM LDK378\nTM ZYKADIA\t@match=ci\nTM 1032900-25-6\nVO CCG</model>
    </docelem>
  </docelems>
</corpus>
"""

client.send(destination=destination, body=body, headers={'transformation': 'TEXT', 'event': 'FoundCorpus'})

print "Updating Ontology"

client.disconnect(receipt='bye')
client.receiveFrame()
client.close()
sys.exit(0)
