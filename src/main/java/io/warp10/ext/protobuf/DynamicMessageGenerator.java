package io.warp10.ext.protobuf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.lang3.StringUtils;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.MapField;

import io.warp10.continuum.gts.GeoTimeSerie.TYPE;
import io.warp10.crypto.SipHashInline;
import io.warp10.ext.protobuf.antlr.Protobuf3Lexer;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.EnumDefinitionContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.EnumFieldContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.FieldContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.FieldOptionContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.MapFieldContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.MessageContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.OneofContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.OneofFieldContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.ProtoContext;
import io.warp10.ext.protobuf.antlr.Protobuf3Parser.TopLevelDefContext;
import io.warp10.script.WarpScriptException;

//
// @see <a href="https://github.com/antlr/grammars-v4/blob/master/protobuf3/Protobuf3.g4">Protobuf3 Grammar</a>
//
public class DynamicMessageGenerator {
  
  private String currentEnum = null;
  
  private FileDescriptorSet set = null;
  private FileDescriptorSet.Builder fds = FileDescriptorSet.newBuilder();
  private FileDescriptorProto.Builder fd = FileDescriptorProto.newBuilder();
  
  private List<DescriptorProto.Builder> currentMessages = new ArrayList<DescriptorProtos.DescriptorProto.Builder>();
  
  private Map<String,Type> knownTypes = new HashMap<String,Type>();
    
  private final String fname;
  public DynamicMessageGenerator(String name) {
    this.fname = name;
    knownTypes.put("double", Type.TYPE_DOUBLE);
    knownTypes.put("float", Type.TYPE_FLOAT);
    knownTypes.put("int32", Type.TYPE_INT32);
    knownTypes.put("int64", Type.TYPE_INT64);
    knownTypes.put("uint32", Type.TYPE_UINT32);
    knownTypes.put("uint64", Type.TYPE_UINT64);
    knownTypes.put("sint32", Type.TYPE_SINT32);
    knownTypes.put("sint64", Type.TYPE_SINT64);
    knownTypes.put("fixed32", Type.TYPE_FIXED32);
    knownTypes.put("fixed64", Type.TYPE_FIXED64);
    knownTypes.put("sfixed32", Type.TYPE_SFIXED32);
    knownTypes.put("sfixed64", Type.TYPE_SFIXED64);
    knownTypes.put("bool", Type.TYPE_BOOL);
    knownTypes.put("string", Type.TYPE_STRING);
    knownTypes.put("bytes", Type.TYPE_BYTES);    
  }
  
  
  public synchronized void generate(Object context) throws WarpScriptException {
    if (null != set) {
      throw new WarpScriptException("generate cannot be called after descriptor set has been built.");
    }
    if (null == context) {
      return;
    }
    
    if (context instanceof ProtoContext) {
      ProtoContext pc = (ProtoContext) context;
      for (TopLevelDefContext tldc: pc.topLevelDef()) {
        generate(tldc);
      }
    } else if (context instanceof TopLevelDefContext) {
      TopLevelDefContext tldc = (TopLevelDefContext) context;

      generate(tldc.enumDefinition());
      generate(tldc.message());
    } else if (context instanceof EnumDefinitionContext) {
      EnumDefinitionContext edc = (EnumDefinitionContext) context;
      
      currentEnum = edc.enumName().getText();
      
      DescriptorProto.Builder currentMessage = null;      
      if (!currentMessages.isEmpty()) {
        currentMessage = currentMessages.get(0);
      }
      
      EnumDescriptorProto.Builder builder = EnumDescriptorProto.newBuilder();
      
      String name = edc.enumName().getText();
      
      builder.setName(name);
      
      for (EnumFieldContext efc: edc.enumBody().enumField()) {
        EnumValueDescriptorProto.Builder evbuilder = EnumValueDescriptorProto.newBuilder();
        evbuilder.setName(efc.Ident().getText());
        if (efc.getChild(2).getText().startsWith("-")) {
          evbuilder.setNumber(Integer.parseInt("-" + efc.IntLit().getText()));
        } else {
          evbuilder.setNumber(Integer.parseInt(efc.IntLit().getText()));          
        }
        builder.addValue(evbuilder);
      }
      
      if (null != currentMessage) {
        currentMessage.addEnumType(builder);
      } else {
        fd.addEnumType(builder);
      }
      if(null != knownTypes.put(name, Type.TYPE_ENUM)) {
        throw new WarpScriptException("Type '" + name + "' already exists.");
      }
    } else if (context instanceof MessageContext) {
      MessageContext mc = (MessageContext) context;
      String name = mc.messageName().getText();
      DescriptorProto.Builder currentMessage = null;
      if (!currentMessages.isEmpty()) {
        currentMessage = currentMessages.get(0);
      }
      DescriptorProto.Builder dpb = DescriptorProto.newBuilder();
      dpb.setName(name);
      
      currentMessages.add(0, dpb);
      
      // Handle inner enums
      for (EnumDefinitionContext edc: mc.messageBody().enumDefinition()) {
        // FIXME(hbs): should we add the enum as a nested enum to dpb?
        generate(edc);
      }
      
      // Handle nested messages
      for (MessageContext mc2: mc.messageBody().message()) {
        // FIXME(hbs): should we add the message as a nested message to dpb?
        generate(mc2);
      }
      
      // Handle fields
      for (FieldContext fc: mc.messageBody().field()) {
        boolean repeated = "repeated".equals(fc.getChild(0).getText());
        String fname = fc.fieldName().getText();
        int number = Integer.parseInt(fc.fieldNumber().getText());
        String type = fc.typeRule().getText();
        
        if (!knownTypes.containsKey(type)) {
          throw new WarpScriptException("Unknown type '" + type + "'.");
        }
        
        String defaultValue = null;
        
        // Extract default value
        if (null != fc.fieldOptions()) {
          for (FieldOptionContext foc: fc.fieldOptions().fieldOption()) {
            if ("default".equals(foc.optionName().getText()) && null != foc.constant()) {
              defaultValue = foc.constant().getText();
            }
          }
        }
        
        FieldDescriptorProto.Builder fbuilder = FieldDescriptorProto.newBuilder();
        if (repeated) {
          fbuilder.setLabel(Label.LABEL_REPEATED);
        }
        if (null != defaultValue) {
          fbuilder.setDefaultValue(defaultValue);
        }
        fbuilder.setName(fname);
        fbuilder.setNumber(number);
        Type t = knownTypes.get(type);
        if (Type.TYPE_ENUM == t || Type.TYPE_MESSAGE == t) {
          fbuilder.setTypeName(type);
        }
        fbuilder.setType(t);
        dpb.addField(fbuilder);
      }
      
      // Handle map fields
      for (MapFieldContext mfc: mc.messageBody().mapField()) {
        String fname = mfc.mapName().getText();
        int number = Integer.parseInt(mfc.fieldNumber().getText());
        String keyType = mfc.keyType().getText();
        String valueType = mfc.typeRule().getText();
        
        if (!knownTypes.containsKey(valueType)) {
          throw new WarpScriptException("Unknown map value type '" + valueType + "'.");
        }
        
        String mapEntryName = StringUtils.capitalize(fname) + "Entry";
        
        DescriptorProto.Builder mapEntry = DescriptorProto.newBuilder();
        mapEntry.setName(mapEntryName);
        mapEntry.setOptions(MessageOptions.newBuilder().setMapEntry(true).build());
        FieldDescriptorProto.Builder keyField = FieldDescriptorProto.newBuilder();
        keyField.setName(TOPB.MAP_KEY_KEY);
        keyField.setType(knownTypes.get(keyType));
        keyField.setNumber(1);
        mapEntry.addField(keyField);
        FieldDescriptorProto.Builder valueField = FieldDescriptorProto.newBuilder();
        valueField.setName(TOPB.MAP_VALUE_KEY);
        valueField.setType(knownTypes.get(valueType));
        if (Type.TYPE_ENUM == knownTypes.get(valueType) || Type.TYPE_MESSAGE == knownTypes.get(valueType)) {
          keyField.setTypeName(valueType);
        }
        valueField.setNumber(2);
        mapEntry.addField(valueField);        
        dpb.addNestedType(mapEntry);
        
        FieldDescriptorProto.Builder fbuilder = FieldDescriptorProto.newBuilder();
        fbuilder.setLabel(Label.LABEL_REPEATED);
        fbuilder.setName(fname);
        fbuilder.setNumber(number);
        fbuilder.setType(Type.TYPE_MESSAGE);
        fbuilder.setTypeName(mapEntryName);
        
        dpb.addField(fbuilder);
      }
      
      // Handle oneofs
      int oneofidx = 0;
      for(OneofContext oc: mc.messageBody().oneof()) {
        OneofDescriptorProto.Builder odpb = dpb.addOneofDeclBuilder();
        odpb.setName(oc.oneofName().getText());

        for (OneofFieldContext ofc: oc.oneofField()) {
          boolean repeated = "repeated".equals(ofc.getChild(0).getText());
          String fname = ofc.fieldName().getText();
          int number = Integer.parseInt(ofc.fieldNumber().getText());
          String type = ofc.typeRule().getText();
          
          if (!knownTypes.containsKey(type)) {
            throw new WarpScriptException("Unknown type '" + type + "'.");
          }
          
          FieldDescriptorProto.Builder fbuilder = FieldDescriptorProto.newBuilder();
          if (repeated) {
            fbuilder.setLabel(Label.LABEL_REPEATED);
          }
          fbuilder.setName(fname);
          fbuilder.setNumber(number);
          Type t = knownTypes.get(type);
          if (Type.TYPE_ENUM == t || Type.TYPE_MESSAGE == t) {
            fbuilder.setTypeName(type);
          }
          fbuilder.setType(t);
          fbuilder.setOneofIndex(oneofidx);
          dpb.addField(fbuilder);
        }
        oneofidx++;
      }
      
      if (null == currentMessage) {
        fd.addMessageType(dpb);
      } else {
        currentMessage.addNestedType(dpb);
      }
      currentMessages.remove(0);
      if (!currentMessages.isEmpty()) {
        String hierarchy = "";
        for (int i = currentMessages.size() - 1; i >= 0; i--) {
          hierarchy = hierarchy + currentMessages.get(i).getName() + ".";
        }
        name = hierarchy + name;
      }
      if(null != knownTypes.put(name, Type.TYPE_MESSAGE)) {
        throw new WarpScriptException("Type '" + name + "' already exists.");
      }
    }    
  }
  
  public synchronized FileDescriptorSet getDescriptor() {
    if (null == set) {
      fd.setName(ProtobufWarpScriptExtension.PROTOC + "@" + fname);
      fds.addFile(fd);
      set = fds.build();
    }
    return set;
  }
}
