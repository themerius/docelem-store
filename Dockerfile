FROM java:8

ADD ./target/scala-2.11/docelem-store.jar /opt/docelem-store.jar

CMD java -jar /opt/docelem-store.jar
