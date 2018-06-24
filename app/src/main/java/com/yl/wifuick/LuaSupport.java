package com.yl.wifuick;


import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaState;
import org.keplerproject.luajava.LuaStateFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

public class LuaSupport implements Runnable {
    public static int MSG_TYPE_LUA = 1001;

    public LuaState L;
    private Context ctx;
    private Handler hdl;
    private List<String> cmds;
    private StringBuilder output = new StringBuilder();

    public LuaSupport(Context ctx, Handler hdl) {
        this.ctx = ctx;
        this.hdl = hdl;
        this.cmds = new LinkedList<>();
    }

    private synchronized void setCmd(String s) {
        cmds.add(s);
    }

    private synchronized String getCmd() {
        if (cmds.size() > 0) {
            return cmds.remove(0);
        } else {
            return null;
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    private void initLua() {
        L = LuaStateFactory.newLuaState();
        if (L == null) {
            Toast.makeText(ctx, "init lua fail", Toast.LENGTH_LONG).show();
            return;
        }
        L.openLibs();

        try {
            L.pushJavaObject(ctx);
            L.setGlobal("activity");

            new JavaFunction(L) {
                @Override
                public int execute() throws LuaException {
                    for (int i = 2; i <= L.getTop(); i++) {
                        int type = L.type(i);
                        String stype = L.typeName(type);
                        String val = null;
                        if (stype.equals("userdata")) {
                            Object obj = L.toJavaObject(i);
                            if (obj != null)
                                val = obj.toString();
                        } else if (stype.equals("boolean")) {
                            val = L.toBoolean(i) ? "true" : "false";
                        } else {
                            val = L.toString(i);
                        }
                        if (val == null)
                            val = stype;
                        output.append(val);
                        output.append("\t");
                    }
                    Toast.makeText(ctx, output.toString(), Toast.LENGTH_SHORT).show();
                    return 0;
                }
            }.register("print");

            new JavaFunction(L) {
                @Override
                public int execute() throws LuaException {
                    StringBuilder out = new StringBuilder();
                    for (int i = 2; i <= L.getTop(); i++) {
                        int type = L.type(i);
                        String stype = L.typeName(type);
                        String val = null;
                        if (stype.equals("userdata")) {
                            Object obj = L.toJavaObject(i);
                            if (obj != null)
                                val = obj.toString();
                        } else if (stype.equals("boolean")) {
                            val = L.toBoolean(i) ? "true" : "false";
                        } else {
                            val = L.toString(i);
                        }
                        if (val == null)
                            val = stype;
                        out.append(val);
                    }
                    Message msg = new Message();
                    msg.what = MSG_TYPE_LUA;
                    msg.obj = out.toString();
                    hdl.sendMessage(msg);
                    return 0;
                }
            }.register("jdump");

            new JavaFunction(L) {
                @Override
                public int execute() throws LuaException {
                    L.pushString(getCmd());
                    return 1;
                }
            }.register("getcmd");

            JavaFunction assetLoader = new JavaFunction(L) {
                @Override
                public int execute() throws LuaException {
                    String name = L.toString(-1);

                    AssetManager am = ctx.getAssets();
                    try {
                        InputStream is = am.open("lua/" + name + ".lua");
                        byte[] bytes = readAll(is);
                        L.LloadBuffer(bytes, name);
                        return 1;
                    } catch (Exception e) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        e.printStackTrace(new PrintStream(os));
                        L.pushString("Cannot load module " + name + ":\n" + os.toString());
                        return 1;
                    }
                }
            };

            L.getGlobal("package");                 // package
            L.getField(-1, "loaders");      // package loaders
            int nLoaders = L.objLen(-1);       // package loaders

            L.pushJavaFunction(assetLoader);        // package loaders loader
            L.rawSetI(-2, nLoaders + 1);    // package loaders
            L.pop(1);                            // package

            L.getField(-1, "path");         // package path
            String customPath = ctx.getFilesDir() + "/?.lua";
            L.pushString(";" + customPath);         // package path custom
            L.concat(2);                         // package pathCustom
            L.setField(-2, "path");         // package
            L.pop(1);
            //
            evalStr("require('main')");
        } catch (Exception e) {
            Toast.makeText(ctx, "Cannot override print", Toast.LENGTH_SHORT);
        }
    }

    private String evalStr(String src) {
        String res = null;
        try {
            res = evalLua(src);
        } catch (LuaException e) {
            res = e.getMessage() + "\n";
        }
        return res;
    }

    private String evalLua(String src) throws LuaException {
        L.setTop(0);
        int ok = L.LloadString(src);
        if (ok == 0) {
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);
            ok = L.pcall(0, 0, -2);
            if (ok == 0) {
                String res = output.toString();
                output.setLength(0);
                return res;
            }
        }
        throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    }

    private String errorReason(int error) {
        switch (error) {
            case 4:
                return "Out of memory";
            case 3:
                return "Syntax error";
            case 2:
                return "Runtime error";
            case 1:
                return "Yield error";
        }
        return "Unknown error " + error;
    }

    @Override
    public void run() {
        initLua();
    }

    public void eval(String s) {
        setCmd(s);
    }
}
