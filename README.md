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

## Example Query

    <query>
     <layer>Alpha</layer>
     <annot>contains://scai.fraunhofer.de/terminology/MeSH.syn</annot>
     <annot>contains://scai.fraunhofer.de/terminology/Homo_sapiens.syn</annot>
    </query>

    <query>
     <layer>ccg-run-2</layer>
     <annot>contains://scai.fraunhofer.de/terminology/cancer</annot>
    </query>
