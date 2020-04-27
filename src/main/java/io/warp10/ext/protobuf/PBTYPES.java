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
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public class PBTYPES extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  public PBTYPES(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    Object top = stack.pop();
    
    if (!(top instanceof ProtoDesc)) {
      throw new WarpScriptException(getName() + " expects a PROTO instance.");
    }
    
    ProtoDesc pbdesc = (ProtoDesc) top;
    
    Map<Object,Object> types = new HashMap<Object,Object>();
    
    types.put("messages", pbdesc.getTypes());
    types.put("enums", pbdesc.getEnums());
    
    stack.push(types);
    
    return stack;
  }
}
