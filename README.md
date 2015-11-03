# docelem-store

## Running

To start this service you need a running broker like `Apache Apollo`.

First of all start this microservice with:

    ./activator run

Start with customized config:

    ./activator run -Dconfig.file=/path/to/application.conf

Note: You override single config parameters from reference.conf
in your application.conf supplied by the system property.

## Build fat jar

    ./activator assembly
