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
  README.txt               This file.

Requirements
------------
  Java 17 or newer. If it is not installed yet, get it from
  https://adoptium.net and install it before starting the application.

How to start
------------
  1. Extract this ZIP archive into any folder.
  2. macOS    : double-click "run.command".
     Windows  : double-click "run.bat".
     Linux    : run "./run.sh" from a terminal in that folder.

The launcher checks that Java is available and then starts vsum.jar.
Keep all files together in the same folder - the launchers expect vsum.jar
to sit next to them.

API collection (Postman)
------------------------
  "postman_API_collection" is a Postman v2.1 collection that lets you call the
  running application without writing any request by hand. It covers the view
  API: health, view types, view selector, opening and refreshing a view, and
  deriving changes.

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
  receive into them.

Advanced
--------
  Extra JVM options can be passed through the JAVA_OPTS environment variable,
  for example:

    JAVA_OPTS="-Xmx2g" ./run.sh          (macOS / Linux)
    set JAVA_OPTS=-Xmx2g && run.bat      (Windows)

  Any argument given to a launcher is forwarded to the application:

    ./run.sh --server.port=9090
