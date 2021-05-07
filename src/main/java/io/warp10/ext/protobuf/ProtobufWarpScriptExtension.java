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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;

import io.warp10.WarpConfig;
import io.warp10.continuum.gts.ValueEncoder;
import io.warp10.ext.protobuf.PROTOC.ProtoDesc;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.functions.SNAPSHOT;
import io.warp10.script.functions.TYPEOF;
import io.warp10.warp.sdk.WarpScriptExtension;

public class ProtobufWarpScriptExtension extends WarpScriptExtension {
  
  private static final String CONFIG_VALUE_ENCODER = "protobuf.value.encoder";
  
  public static final String PROTO_TYPEOF = "PROTO";
  public static final String DESC_TYPEOF = "PROTODESC";
  public static final String PROTOC = "PROTOC";
  public static final String PBTYPE = "PBTYPE";
  public static final String TOPB = "->PB";
  
  private static final Map<String,Object> functions;
  
  static {
    functions = new HashMap<String,Object>();
    
    functions.put(PROTOC, new PROTOC(PROTOC));
    functions.put("PB->", new PBTO("PB->"));
    functions.put("->PB", new TOPB(TOPB));
    functions.put("PBTYPES", new PBTYPES("PBTYPES"));
    functions.put("PBDUMP", new PBDUMP("PBDUMP"));
    functions.put(PBTYPE, new PBTYPE(PBTYPE));
    
    TYPEOF.addResolver(new TYPEOF.TypeResolver() {      
      @Override
      public String typeof(Class clazz) {
        if (clazz.isAssignableFrom(ProtoDesc.class)) {
          return PROTO_TYPEOF;
        } else if (clazz.isAssignableFrom(Descriptor.class)) {
          return DESC_TYPEOF;
        }
        return null;
      }
    });
    
    SNAPSHOT.addEncoder(new SNAPSHOT.SnapshotEncoder() {
      
      @Override
      public boolean addElement(SNAPSHOT snapshot, StringBuilder sb, Object obj, boolean readable) throws WarpScriptException {
        if (obj instanceof Descriptor) {
          Descriptor desc = (Descriptor) obj;
          DescriptorProto dp = desc.toProto();
          FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
          builder.addMessageType(desc.toProto());
          FileDescriptorProto fdp = builder.build();
          FileDescriptorSet.Builder fdsbuilder = FileDescriptorSet.newBuilder();
          fdsbuilder.addFile(fdp);
          snapshot.addElement(sb, fdsbuilder.build().toByteArray());
          sb.append(" ");
          sb.append(PROTOC);
          sb.append(" ");
          snapshot.addElement(sb, desc.getName());
          sb.append(PBTYPE);
          sb.append(" ");
          return true;
        }
        
        return false;
      }
    });
    
    if ("true".equals(WarpConfig.getProperty(CONFIG_VALUE_ENCODER))) {
      ValueEncoder.register(new ProtobufValueEncoder());
    }
  }
  
  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }
}
