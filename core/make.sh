protoc -I=./ --java_out=./ src/main/proto/session.proto 
protoc --js_out=import_style=commonjs,binary:. src/main/proto/session.proto
mv src/main/proto/session_pb.js ../api/src/main/resources/static/js/
mv io/dataguardians/protobuf/Session.java src/main/java/io/dataguardians/protobuf
pushd ../api/
browserify ./src/main/resources/static/js/session_pb.js -o ./src/main/resources/static/js/bundled_session_pb.js
popd
