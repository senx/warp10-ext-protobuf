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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.math3.FieldElement;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.common.primitives.Longs;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import io.warp10.crypto.SipHashInline;
import io.warp10.ext.protobuf.antlr.Protobuf3Lexer;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.ProtoContext;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.functions.SNAPSHOT;
import io.warp10.script.functions.SNAPSHOT.Snapshotable;

public class PROTOC extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  private static long k0 = 0x123456789ABCDEF0L;
  private static long k1 = 0xFEDCBA9876543210L;
  
  public static class ProtoDesc implements Snapshotable {
    private Map<String,FileDescriptor> fileDescriptors = new HashMap<String,FileDescriptor>();
    private byte[] pb = null;
    private String proto = null;
    
    public ProtoDesc(String proto) {
      this.proto = proto;
    }
    
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
    
    public Map<Object,Object> dump() {
      Map<Object,Object> dump = new HashMap<Object,Object>();
      for (Entry<String,FileDescriptor> entry: this.fileDescriptors.entrySet()) {
        dump.put(entry.getKey(), entry.getValue().toProto().toString());
      }
      return dump;
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
    
    public List<String> getEnums() {
      List<String> enums = new ArrayList<String>();
      for (FileDescriptor fd: fileDescriptors.values()) {
        for (EnumDescriptor d: fd.getEnumTypes()) {
          enums.add(d.getName());
        }
      }
      return enums;
    }
    
    @Override
    public String snapshot() {
      StringBuilder sb = new StringBuilder();
      try {
        if (null != proto) {
          SNAPSHOT.addElement(sb, this.proto);
        } else {
          SNAPSHOT.addElement(sb, this.pb);
          sb.append(WarpScriptLib.UNGZIP);        
        }
      } catch (WarpScriptException wse) {
        throw new RuntimeException(wse);
      }
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
    
    if (!(top instanceof byte[]) && !(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a binary representation of a protobuf message descriptor or a STRING containing protobuf syntax.");
    }

    FileDescriptorSet fds = null;
    ProtoDesc pbdesc = null;
    
    try {
      if (top instanceof byte[]) {
        byte[] desc = (byte[]) top;
              
        // @see https://groups.google.com/forum/#!topic/protobuf/YRMJtEIBp0c      
        fds = FileDescriptorSet.parseFrom(desc);
        pbdesc = new ProtoDesc(desc);
      } else {
        byte[] bytes = ((String) top).getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        
        try {
          CharStream input = CharStreams.fromStream(in);
          Protobuf3Lexer lexer = new Protobuf3Lexer(input);
          CommonTokenStream tokens = new CommonTokenStream(lexer);
          Protobuf3Parser parser = new Protobuf3Parser(tokens);
          
          ProtoContext context = parser.proto();

          String name = Hex.encodeHexString(Longs.toByteArray(SipHashInline.hash24(k0, k1, bytes, 0, bytes.length)));
          DynamicMessageGenerator generator = new DynamicMessageGenerator(name);
          
          generator.generate(context);
          fds = generator.getDescriptor();
          pbdesc = new ProtoDesc((String) top);
        } catch (IOException ioe) {
          throw new WarpScriptException(getName() +" error compiling Thrift IDL.", ioe);
        }
      }

      FileDescriptor current = null;
      for (FileDescriptorProto fdp : fds.getFileList()) {
        final List<String> dependencyList = fdp.getDependencyList();
        final FileDescriptor[] fda = new FileDescriptor[dependencyList.size()];
        int idx = 0;
        for (String dep: dependencyList) {
          FileDescriptor fd = pbdesc.getFD(dep);
          if (null == fd) {
            throw new WarpScriptException(getName() + " missing dependency '" + fd + "', need to use the --include_imports directive when running protoc.");
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
          registry.add(field);
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
