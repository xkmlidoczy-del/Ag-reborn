package cn.academy.terminal.item;
public final class ItemApp extends net.minecraft.item.Item { private static final java.util.Map<cn.academy.terminal.App,ItemApp> apps=new java.util.IdentityHashMap<>();private ItemApp(cn.academy.terminal.App app){super("app_"+app.getName());}public static ItemApp getItemForApp(cn.academy.terminal.App app){return apps.computeIfAbsent(app,ItemApp::new);} }
