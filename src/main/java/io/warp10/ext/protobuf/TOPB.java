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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.AbstractMessage.Builder;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import io.warp10.ext.protobuf.PROTOC.ProtoDesc;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.functions.TYPEOF;

public class TOPB extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  //
  // Field names for inner message of map<> fields
  // @see https://developers.google.com/protocol-buffers/docs/proto3#maps
  //
  static final String MAP_KEY_KEY = "key";
  static final String MAP_VALUE_KEY = "value";
  
  public TOPB(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();
    
    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a type name.");
    }
    
    String type = (String) top;
    
    top = stack.pop();
    
    if (!(top instanceof ProtoDesc)) {
      throw new WarpScriptException(getName() + " expects a protobuf descriptor.");
    }
    
    ProtoDesc desc = (ProtoDesc) top;
    
    Descriptor typeDesc = desc.getMessageType(type);
    
    if (null == typeDesc) {
      throw new WarpScriptException(getName() + " unknown type '" + type + "', not in " + desc.getTypes() + ".");
    }
    
    top = stack.pop();
    
    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " operates on a MAP.");
    }

    Map<Object,Object> map = (Map<Object,Object>) top;
    
    Message msg = fromWarpScript(typeDesc, map);
    
    stack.push(msg.toByteArray());
    
    return stack;
  }

  private static Message fromWarpScript(Descriptor type, Map<Object,Object> obj) throws WarpScriptException {    
    Builder builder = DynamicMessage.newBuilder(type);
    List<Object> REPEATED = new ArrayList<Object>();
    
    // Iterator over the fields of 'obj'
    for (Entry<Object,Object> entry: obj.entrySet()) {
      Object name = entry.getKey();
      Object val = entry.getValue();
      if (!(name instanceof String)) {
        throw new WarpScriptException("Invalid key '" + String.valueOf(name) + "', must be a STRING.");
      }

      FieldDescriptor fd = type.findFieldByName((String) name);
      
      if (null == fd) {        
        throw new WarpScriptException("Unknown field '" + name + "'.");
      }
      
      REPEATED.clear();
      List<Object> repeated = REPEATED;
      
      if (fd.isRepeated()) {
        if (!fd.isMapField() && !(val instanceof List)) { 
          throw new WarpScriptException("Field '" + name + "' must be a " + TYPEOF.TYPE_LIST + ".");
        } else if (fd.isMapField()) {
          if (!(val instanceof Map)) {
            throw new WarpScriptException("Field '" + name + "' must be a " + TYPEOF.TYPE_MAP + ".");            
          }
          //
          // We need to convert each key/value into a Map of its own and populate
          // 'repeated' with those maps
          //
          for (Entry<Object,Object> mapentry: ((Map<Object,Object>) val).entrySet()) {
            Map<Object,Object> mapvalue = new HashMap<Object,Object>(2);
            mapvalue.put(MAP_KEY_KEY, mapentry.getKey());
            mapvalue.put(MAP_VALUE_KEY, mapentry.getValue());
            repeated.add(mapvalue);
          }
        } else {
          repeated = (List<Object>) val;          
        }
      } else {
        repeated.add(val);
      }
      
      for (Object value: repeated) {
        switch(fd.getType()) {
          case BOOL:
            if (!(value instanceof Boolean)) {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_BOOLEAN + ".");
            }
            break;
          case BYTES:
            if (!(value instanceof byte[])) {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_BYTES + ".");            
            }
            break;
          case DOUBLE:
            if (!(value instanceof Double)) {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_DOUBLE + ".");            
            }
            break;
          case ENUM:
            EnumValueDescriptor evd = null;
            if (value instanceof String) {
              evd = fd.getEnumType().findValueByName((String) value);
            } else if (value instanceof Long) {
              evd = fd.getEnumType().findValueByNumber(((Long) value).intValue());            
            }
            
            if (null == evd) {
              throw new WarpScriptException("Invalid enum value for field '" + name + "', expecting one of " + enumValues(fd.getEnumType()) + ".");
            }

            value = evd;
            break;
          case FIXED32:
          case FIXED64:
          case FLOAT:
            if (value instanceof Double) {
              value = ((Double) value).floatValue();
            } else {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_DOUBLE + ".");            
            }
            break;
          case GROUP:
          case INT32:
          case SFIXED32:
          case SINT32:
          case UINT32:
            if (value instanceof Long) {
              value = (int) (((Long) value).longValue() & 0xFFFFFFFFL);
            } else {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_LONG + ".");            
            }
            break;
          case INT64:
          case SFIXED64:
          case SINT64:
          case UINT64:          
            if (!(value instanceof Long)) {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_LONG + ".");            
            }
            break;
          case MESSAGE:
            if (!(value instanceof Map)) {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_MAP + ".");
            }
            value = fromWarpScript(fd.getMessageType(), (Map<Object,Object>) value);
            break;
          case STRING:
            if (!(value instanceof String)) {
              throw new WarpScriptException("Invalid value for field '" + name + "', expected " + TYPEOF.TYPE_STRING + ".");            
            }
            break;
        }        
        if (fd.isRepeated()) {
          builder.addRepeatedField(fd, value);
        } else {
          builder.setField(fd, value);
        }
      }
    }
    return builder.build();
  }
  
  private static String enumValues(EnumDescriptor ed) {
    StringBuilder sb = new StringBuilder();
    for (EnumValueDescriptor evd: ed.getValues()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(evd.getName() + "(" + evd.getNumber() + ")");
    }
    return sb.toString();
  }
}
