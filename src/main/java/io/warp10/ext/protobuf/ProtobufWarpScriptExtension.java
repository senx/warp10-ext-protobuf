//
//   Copyright 2020  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.ext.protobuf;

import java.util.HashMap;
import java.util.Map;

import io.warp10.ext.protobuf.PROTOC.ProtoDesc;
import io.warp10.script.functions.TYPEOF;
import io.warp10.warp.sdk.WarpScriptExtension;

public class ProtobufWarpScriptExtension extends WarpScriptExtension {
  
  public static final String PROTO_TYPEOF = "PROTO";
  public static final String PROTOC = "PROTOC";
  
  private static final Map<String,Object> functions;
  
  static {
    functions = new HashMap<String,Object>();
    
    functions.put(PROTOC, new PROTOC(PROTOC));
    functions.put("PB->", new PBTO("PB->"));
    functions.put("->PB", new TOPB("->PB"));
    functions.put("PBTYPES", new PBTYPES("PBTYPES"));
    functions.put("PBDUMP", new PBDUMP("PBDUMP"));
    
    TYPEOF.addResolver(new TYPEOF.TypeResolver() {      
      @Override
      public String typeof(Class clazz) {
        if (clazz.isAssignableFrom(ProtoDesc.class)) {
          return PROTO_TYPEOF;
        }
        return null;
      }
    });
  }
  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }
}
