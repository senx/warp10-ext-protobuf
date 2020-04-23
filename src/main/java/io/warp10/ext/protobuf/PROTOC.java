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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.math3.FieldElement;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.functions.SNAPSHOT;
import io.warp10.script.functions.SNAPSHOT.Snapshotable;

public class PROTOC extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public static class ProtoDesc implements Snapshotable {
    private Map<String,FileDescriptor> fileDescriptors = new HashMap<String,FileDescriptor>();
    private byte[] pb = null;
    
    public ProtoDesc(byte[] pb) {
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzout = new GZIPOutputStream(out);
        gzout.write(pb);
        gzout.close();
      
        this.pb = out.toByteArray();
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
    
    public FileDescriptor put(String f, FileDescriptor fd) {
      return fileDescriptors.put(f, fd);
    }
    
    public FileDescriptor getFD(String key) {
      return fileDescriptors.get(key);
    }
    
    public Descriptor getMessageType(String key) throws WarpScriptException {
      for (FileDescriptor fd: fileDescriptors.values()) {
        Descriptor desc = fd.findMessageTypeByName(key);
        if (null != desc) {
          return desc;
        }
      }
      return null;
    }
    
    public ExtensionRegistry getExtensionRegistry() {
      ExtensionRegistry registry = ExtensionRegistry.newInstance();
      for (FileDescriptor fd: fileDescriptors.values()) {
        for (FieldDescriptor fld: fd.getExtensions()) {
          registry.add(fld);
        }
      }
      return registry;
    }
    
    public List<String> getTypes() {
      List<String> types = new ArrayList<String>();
      for (FileDescriptor fd: fileDescriptors.values()) {
        for (Descriptor d: fd.getMessageTypes()) {
          types.add(d.getName());
        }
      }
      return types;
    }
    
    @Override
    public String snapshot() {
      StringBuilder sb = new StringBuilder();
      try {
        SNAPSHOT.addElement(sb, this.pb);
      } catch (WarpScriptException wse) {
        throw new RuntimeException(wse);
      }
      sb.append(WarpScriptLib.UNGZIP);
      sb.append(" ");
      sb.append(ProtobufWarpScriptExtension.PROTOC);
      sb.append(" ");
      return sb.toString();
    }
  }
  
  public PROTOC(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    Object top = stack.pop();
    
    if (!(top instanceof byte[])) {
      throw new WarpScriptException(getName() + " expects a binary representation of a protobuf message descriptor.");
    }
    
    byte[] desc = (byte[]) top;
        
    try {
      FileDescriptorSet fds = FileDescriptorSet.parseFrom(desc);

      // @see https://groups.google.com/forum/#!topic/protobuf/YRMJtEIBp0c
      
      ProtoDesc pbdesc = new ProtoDesc(desc);
      
      FileDescriptor current = null;
      for (FileDescriptorProto fdp : fds.getFileList()) {
        final List<String> dependencyList = fdp.getDependencyList();
        final FileDescriptor[] fda = new FileDescriptor[dependencyList.size()];
        int idx = 0;
        for (String dep: dependencyList) {
          FileDescriptor fd = pbdesc.getFD(dep);
          if (null == fd) {
            throw new WarpScriptException(getName() + " missing dependency '" + fd + "', need to use the --include imports directive when running protoc.");
          } else {
            fda[idx++] = fd;
          }
        }
        current = FileDescriptor.buildFrom(fdp, fda, false);
        
        // Register extensions
        // @see https://developers.google.com/protocol-buffers/docs/proto#extensions
        // @see https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/ExtensionRegistry      
        // @see https://groups.google.com/forum/#!topic/protobuf/zKYLsr9xE90
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        for (FieldDescriptor field: current.getExtensions()) {
          
        }
        pbdesc.put(current.getName(), current);
      }
      
      stack.push(pbdesc);
    } catch (InvalidProtocolBufferException ipbe) {
      throw new WarpScriptException(getName() + " found invalid protobuf content.", ipbe);
    } catch (DescriptorValidationException dve) {
      throw new WarpScriptException(getName() + " failed to validate protobuf descriptor.", dve);
    }
    
    return stack;
  }

}
