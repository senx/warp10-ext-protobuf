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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import io.warp10.ext.protobuf.PROTOC.ProtoDesc;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public class PBTO extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public PBTO(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();
    
    Descriptor typeDesc = null;
    ProtoDesc desc = null;
    
    if (top instanceof Descriptor) {
      typeDesc = (Descriptor) top;
    } else {
      if (!(top instanceof String)) {
        throw new WarpScriptException(getName() + " expects a type name.");
      }
      
      String type = (String) top;
      
      top = stack.pop();
      
      if (!(top instanceof ProtoDesc)) {
        throw new WarpScriptException(getName() + " expects a protobuf descriptor.");
      }
      
      desc = (ProtoDesc) top;
      
      typeDesc = desc.getMessageType(type);
      
      if (null == typeDesc) {
        throw new WarpScriptException(getName() + " unknown type '" + type + "', not in " + desc.getTypes() + ".");
      }      
    }
    
    top = stack.pop();
    
    if (!(top instanceof byte[])) {
      throw new WarpScriptException(getName() + " operates on a byte array.");
    }
    
    byte[] data = (byte[]) top;
    
    ExtensionRegistry extensionRegistry = null != desc ? desc.getExtensionRegistry() : null;
    
    try {
      DynamicMessage dm;
      
      if (null != extensionRegistry) {
        dm = DynamicMessage.parseFrom(typeDesc, data, extensionRegistry);
      } else {
        dm = DynamicMessage.parseFrom(typeDesc, data);
      }
      stack.push(toWarpScript(dm));
    } catch (InvalidProtocolBufferException ipbe) {
      throw new WarpScriptException(getName() + " invalid content.", ipbe);
    }
    
    return stack;
  }

  
  private static Object toWarpScript(Object obj) {    
    if (obj instanceof DynamicMessage) {
      DynamicMessage msg = (DynamicMessage) obj;
      Map<String,Object> map = new HashMap<String,Object>();

      for (Entry<FieldDescriptor,Object> field: msg.getAllFields().entrySet()) {
        String name = field.getKey().getName();
        Object value = field.getValue();
        
        if (value instanceof List) {
          List<Object> l = (List<Object>) value;
          
          if (!field.getKey().isMapField()) {
            List<Object> ll = new ArrayList<Object>(l.size());
            for (Object o: l) {
              ll.add(toWarpScript(o));
            }
            map.put(name, ll);            
          } else {
            Map<Object,Object> m = new LinkedHashMap<Object,Object>(l.size());
            for (Object o: l) {
              Map<Object,Object> valmap = (Map<Object,Object>) toWarpScript(o);
              m.put(valmap.get(TOPB.MAP_KEY_KEY), valmap.get(TOPB.MAP_VALUE_KEY));
            }
            map.put(name, m);
          }
          
          // TODO(hbs): check if field.getKey().isMapField() is true and
          //            generate a Map instead of a List
        } else {
          map.put(name, toWarpScript(value));
        }
      }
      
      return map;
    } else if (obj instanceof Double || obj instanceof Float) {
      return ((Number) obj).doubleValue();
    } else if (obj instanceof Integer || obj instanceof Long) {
      return ((Number) obj).longValue();
    } else if (obj instanceof String
        || obj instanceof Boolean) {
      return obj;
    } else if (obj instanceof ByteString) {
      return ((ByteString) obj).toByteArray();
    } else if (obj instanceof EnumValueDescriptor) {
      EnumValueDescriptor evd = ((EnumValueDescriptor) obj);
      return (evd.getName());
    } else {      
      throw new RuntimeException("Unsupported type " + obj.getClass());
    }
  }
}
