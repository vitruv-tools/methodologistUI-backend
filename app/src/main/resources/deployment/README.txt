VSUM deployment bundle
======================

This archive contains everything needed to run your VSUM application on your
own machine.

Contents
--------
  vsum.jar                 The application built from your VSUM.
  run.command              Start the application on macOS (double-click).
  run.sh                   Start the application on Linux.
  run.bat                  Start the application on Windows (double-click).
  postman_API_collection   Postman collection for the application's REST API.
  Dockerfile               Image definition to run the application in Docker.
  docker-compose.yaml      Starts that image with one command (see below).
  README.txt               This file.

Requirements
------------
  Java 21 or newer. If it is not installed yet, get it from
  https://adoptium.net and install it before starting the application.

  Alternatively run the application in Docker (see "Run in Docker" below);
  then only Docker is needed, no local Java installation.

How to start
------------
  1. Extract this ZIP archive into any folder.
  2. macOS    : double-click "run.command".
     Windows  : double-click "run.bat".
     Linux    : run "./run.sh" from a terminal in that folder.

The launcher checks that Java is available and then starts vsum.jar.
Keep all files together in the same folder - the launchers expect vsum.jar
to sit next to them.

Run in Docker
-------------
  With Docker (including Compose v2) installed, run from this folder:

    docker compose up --build -d

  This builds an image from the Dockerfile, starts the application as a
  container and publishes it on http://localhost:8080 - check with

    curl http://localhost:8080/health

  Useful commands:

    docker compose logs -f          follow the application log
    docker compose down             stop the container
    docker compose down -v          stop it and delete the stored models

  The application keeps its models in the Docker volume "vsum-data", so they
  survive restarts and image rebuilds.

  To use another host port, set VSUM_PORT; JVM options go into
  JAVA_TOOL_OPTIONS (default -Xmx1g):

    VSUM_PORT=9000 JAVA_TOOL_OPTIONS="-Xmx2g" docker compose up --build -d

  Without Compose:

    docker build -t my-vsum .
    docker run -d -p 8080:8080 -v vsum-data:/data my-vsum

API collection (Postman)
------------------------
  "postman_API_collection" is a Postman v2.1 collection that lets you call the
  running application without writing any request by hand. It covers the view
  API (health, view types, view selector, opening and refreshing a view,
  deriving changes) and the constraint API (see "Constraints" below).

  To use it:
  1. Start the application as described above.
  2. In Postman choose Import and select the "postman_API_collection" file
     (it is a JSON file; Postman detects the format from its content).
  3. Open the collection variables and fill in at least "baseVitru" with the
     base URL the application prints on startup, for example
     http://localhost:8080

  The remaining variables ("baseVapp", "selector-uuid", "select-id-1",
  "select-id-2", "view-uuid") are placeholders that get their values from
  earlier responses - run the requests from top to bottom and copy the ids you
  receive into them. "constraint-id" is the URL-encoded id of the constraint
  the constraint requests work on, "model-id" the URL-encoded id of a model
  (see "Models" below) and "metamodel-nsuri" the URL-encoded namespace URI of
  a metamodel.

Constraints
-----------
  The constraint rule sets of your VSUM are built into the application. Each
  invariant is a constraint with the id <metamodel>::<Class>::<invariantName>,
  e.g. model::Component::hasName.

  List the constraints:

    curl http://localhost:8080/vsum/constraints

  Validate all constraints against the current models:

    curl -X POST -H "Content-Type: application/json" -d "{}" http://localhost:8080/vsum/constraints/validate

  The answer tells per constraint whether it is SATISFIED, VIOLATED or could
  not be checked (ERROR). Every violation names the UUID of the violating
  element, its class and its model. Send
  {"constraintIds": ["model::Component::hasName"]} to validate selected
  constraints, and "includeModels": true to also receive the affected models,
  in which every element carries its UUID as "_id".

  Constraints can also be created (POST /vsum/constraints), changed
  (PUT /vsum/constraints/<id>) and deleted (DELETE /vsum/constraints/<id>)
  while the application is running; ids in paths are URL-encoded, e.g.
  model%3A%3AComponent%3A%3AhasName. Such changes are stored with the models
  and survive a restart. The Postman collection contains all these requests.

  To monitor the consistency, get only the status of all constraints:

    curl http://localhost:8080/vsum/constraints/status

  or subscribe to it; the status is sent right away and after every change:

    curl -N http://localhost:8080/vsum/constraints/events

Models
------
  The models of the application can be read directly:

    curl http://localhost:8080/vsum/models
    curl http://localhost:8080/vsum/models/%2Fmodel%2Fexample.model
    curl -H "Accept: application/xmi+xml" http://localhost:8080/vsum/models/%2Fmodel%2Fexample.model
    curl http://localhost:8080/vsum/metamodels

  The id of a model equals the model of a constraint violation, and every
  element carries its UUID ("_id" in JSON, "xmi:id" in XMI), so violating
  elements can be located in the returned models.

Advanced
--------
  Extra JVM options can be passed through the JAVA_OPTS environment variable,
  for example:

    JAVA_OPTS="-Xmx2g" ./run.sh          (macOS / Linux)
    set JAVA_OPTS=-Xmx2g && run.bat      (Windows)

  Any argument given to a launcher is forwarded to the application:

    ./run.sh --server.port=9090
