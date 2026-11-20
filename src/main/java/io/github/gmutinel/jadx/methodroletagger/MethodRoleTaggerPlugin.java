package io.github.gmutinel.jadx.methodroletagger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.SimpleJadxPassInfo;
import jadx.api.plugins.pass.types.JadxDecompilePass;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;

/**
 * jadx plugin that tags every decompiled method with a structural role
 * (override/synthetic/constructor/method) and an IR-based complexity score
 * (basic block count), writing the result as a JSON sidecar file.
 *
 * <p>Runs as a {@link JadxDecompilePass}: {@code visit(MethodNode)} fires for
 * every method during the normal decompilation pipeline, after jadx's own
 * {@code OverrideMethodVisitor} has already attached override metadata.
 * Results are accumulated in memory and flushed to disk in {@link #unload()},
 * which jadx calls when the decompiler instance closes/resets.
 */
public class MethodRoleTaggerPlugin implements JadxPlugin {

	private static final Logger LOG = LoggerFactory.getLogger(MethodRoleTaggerPlugin.class);
	private static final String OUTPUT_FILE_NAME = "method_roles.json";

	/**
	 * Filled from jadx's decompile threads. jadx visits most methods more than once, and how many times depends on
	 * the thread schedule: identical records are kept once, in sorted order, so the sidecar is the same on every run.
	 */
	private final SortedSet<String> records = Collections.synchronizedSortedSet(new TreeSet<>());
	private volatile Path outputPath;

	@Override
	public JadxPluginInfo getPluginInfo() {
		return new JadxPluginInfo("method-role-tagger", "Method Role Tagger",
				"Tags methods with structural role (override/synthetic/constructor/method) and IR complexity");
	}

	@Override
	public void init(JadxPluginContext context) {
		context.addPass(new JadxDecompilePass() {
			@Override
			public JadxPassInfo getInfo() {
				return new SimpleJadxPassInfo("MethodRoleTagger", "Tag method roles and complexity");
			}

			@Override
			public void init(RootNode root) {
				Path outDir = root.getArgs().getOutDirSrc() != null
						? root.getArgs().getOutDirSrc().toPath()
						: root.getArgs().getOutDir().toPath();
				outputPath = outDir.resolve(OUTPUT_FILE_NAME);
			}

			@Override
			public boolean visit(ClassNode cls) {
				return true;
			}

			@Override
			public void visit(MethodNode mth) {
				try {
					String role = classify(mth);
					int complexity = complexityOf(mth);
					records.add(toJson(mth, role, complexity));
				} catch (Exception e) {
					LOG.debug("Failed to tag method {}: {}", mth, e.getMessage());
				}
			}
		});
	}

	private static String classify(MethodNode mth) {
		if (mth.get(AType.METHOD_OVERRIDE) != null) {
			return "override";
		}
		AccessInfo af = mth.getAccessFlags();
		if (af.isSynthetic() || af.isBridge()) {
			return "synthetic";
		}
		if (mth.isConstructor()) {
			return "constructor";
		}
		return "method";
	}

	private static int complexityOf(MethodNode mth) {
		List<BlockNode> blocks = mth.getBasicBlocks();
		return blocks == null ? -1 : blocks.size();
	}

	private static String toJson(MethodNode mth, String role, int complexity) {
		String cls = escape(mth.getParentClass().getClassInfo().getAliasFullName());
		String name = escape(mth.getAlias());
		String signature = escape(mth.getMethodInfo().getShortId());
		return String.format(Locale.ROOT,
				"{\"class\":\"%s\",\"method\":\"%s\",\"signature\":\"%s\",\"role\":\"%s\",\"complexity\":%d}",
				cls, name, signature, role, complexity);
	}

	/** A JSON string body: quote, backslash and every control character escaped. */
	private static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': b.append("\\\""); break;
				case '\\': b.append("\\\\"); break;
				case '\n': b.append("\\n"); break;
				case '\r': b.append("\\r"); break;
				case '\t': b.append("\\t"); break;
				default:
					if (c < 0x20) {
						b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						b.append(c);
					}
			}
		}
		return b.toString();
	}

	@Override
	public void unload() {
		Path path = outputPath;
		List<String> sorted;
		synchronized (records) {
			sorted = new ArrayList<>(records);
		}
		if (path == null || sorted.isEmpty()) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				w.write("[\n");
				for (int i = 0; i < sorted.size(); i++) {
					w.write("  ");
					w.write(sorted.get(i));
					w.write(i < sorted.size() - 1 ? ",\n" : "\n");
				}
				w.write("]\n");
			}
			LOG.info("Wrote {} method role tags to {}", sorted.size(), path);
		} catch (IOException e) {
			LOG.error("Failed to write method role tags", e);
		} finally {
			records.clear();
		}
	}
}
