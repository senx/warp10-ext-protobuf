# Protocol Buffers support in WarpScript

This extension adds the possibility from within WarpScript to serialize and deserialize data using the [Protocol Buffers](https://developers.google.com/protocol-buffers/docs/proto) format.

The traditional workflow when working with Procotol Buffers is to generate code for your target language using a `.proto` file as input. Then within your application the generated structure can be manipulated and a set of utility classes can be used to perform serialization and deserialization.

The approach adopted in WarpScript is similar except the code generation step has been removed. Instead the function `PROTOC` can be used to generate an internal WarpScript structure that can be fed as input to serialization (`->PB`) and deserialization (`PB->`) functions. This approach renders the use of Protocol Buffers completely dynamic, enabling runtime generation of serialization/deserialization templates.

To the best of our knowledge this is the first attempt at a fully dynamic use of Protocol Buffers in any data environment. No doubt this feature will have many fans!
