package io.warp10.ext.protobuf;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.google.protobuf.Descriptors.Descriptor;

import io.warp10.WarpConfig;
import io.warp10.continuum.gts.ValueEncoder;
import io.warp10.script.MemoryWarpScriptStack;
import io.warp10.script.WarpScriptException;

public class ProtobufValueEncoder extends ValueEncoder {

  private final TOPB TOPB = new TOPB(ProtobufWarpScriptExtension.TOPB);

  private static final String PB_PREFIX = ":pb:";

  private static final String THREAD_PROPERTY_UUID = "pb.uuid";
  private static final String THREAD_PROPERTY_STACK = "pb.stack";
  private static final String THREAD_PROPERTY_DESCRIPTORS = "pb.descriptors";

  @Override
  public Object parseValue(String strval) throws Exception {

    try {
    // We only support values with the following format:
    // :pb:macro:warpscript

    if (!strval.startsWith(PB_PREFIX)) {
      return null;
    }

    String pbuuid = (String) WarpConfig.getThreadProperty(THREAD_PROPERTY_UUID);

    MemoryWarpScriptStack stack = (MemoryWarpScriptStack) WarpConfig.getThreadProperty(THREAD_PROPERTY_STACK);

    //
    // If the recorded stack uuid for the current thread differs from the uuid of the stack associated
    // with the current thread, clear the attributes
    //

    if (null == stack || !stack.getUUID().equals(pbuuid)) {
      WarpConfig.removeThreadProperty(THREAD_PROPERTY_STACK);
      WarpConfig.removeThreadProperty(THREAD_PROPERTY_DESCRIPTORS);
      stack = null;
    }

    if (null == stack) {
      stack = new MemoryWarpScriptStack(null, null);
      WarpConfig.setThreadProperty(THREAD_PROPERTY_STACK, stack);
      WarpConfig.setThreadProperty(THREAD_PROPERTY_UUID, stack.getUUID());
    }

    Map<String,Descriptor> descriptors = (Map<String,Descriptor>) WarpConfig.getThreadProperty(THREAD_PROPERTY_DESCRIPTORS);

    if (null == descriptors) {
      descriptors = new HashMap<String,Descriptor>();
      WarpConfig.setThreadProperty(THREAD_PROPERTY_DESCRIPTORS, descriptors);
    }

    // Extract the macro name
    String macro = strval.substring(PB_PREFIX.length(), strval.indexOf(':', PB_PREFIX.length()));

    // Check if we already have a Descriptor for the given macro name
    Descriptor descriptor = descriptors.get(macro);

    if (null == descriptor) {
      // No, attempt to retrieve it
      // First clear the stack
      stack.clear();
      stack.getSymbolTable().clear();
      stack.getDefined().clear();
      stack.run(macro);
      Object top = stack.pop();
      if (!(top instanceof Descriptor)) {
        throw new WarpScriptException("Macro @" + macro + " did not return a " + ProtobufWarpScriptExtension.DESC_TYPEOF + ".");
      }
      descriptor = (Descriptor) top;
      descriptors.put(macro, descriptor);
    }

    // Now attempt to serialize the value
    stack.clear();
    stack.getSymbolTable().clear();
    stack.getDefined().clear();
    stack.execMulti(strval.substring(PB_PREFIX.length() + macro.length() + 1));
    stack.push(descriptor);
    TOPB.apply(stack);
    return stack.pop();
    } catch (Throwable t) {
      t.printStackTrace();
      throw t;
    }
  }
}
