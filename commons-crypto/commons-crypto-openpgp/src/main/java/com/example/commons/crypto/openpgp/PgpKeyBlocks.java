package com.example.commons.crypto.openpgp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;

/**
 * Reads every key ring from a stream, including from several ASCII armor blocks one after
 * the other, which Bouncy Castle's own collections stop reading after the first.
 */
final class PgpKeyBlocks {

	private PgpKeyBlocks() {
	}

	static List<PGPPublicKeyRing> publicRings(InputStream in) throws IOException {
		List<PGPPublicKeyRing> rings = new ArrayList<>();
		for (Object object : objects(in)) {
			if (object instanceof PGPPublicKeyRing ring) {
				rings.add(ring);
			}
		}
		return rings;
	}

	static List<PGPSecretKeyRing> secretRings(InputStream in) throws IOException {
		List<PGPSecretKeyRing> rings = new ArrayList<>();
		for (Object object : objects(in)) {
			if (object instanceof PGPSecretKeyRing ring) {
				rings.add(ring);
			}
		}
		return rings;
	}

	private static List<Object> objects(InputStream in) throws IOException {
		InputStream decoder = PGPUtil.getDecoderStream(in);
		List<Object> objects = new ArrayList<>();
		boolean more;
		do {
			int before = objects.size();
			JcaPGPObjectFactory factory = new JcaPGPObjectFactory(decoder);
			for (Object object = factory.nextObject(); object != null; object = factory.nextObject()) {
				objects.add(object);
			}
			more = decoder instanceof ArmoredInputStream armored && !armored.isEndOfStream() && objects.size() > before;
		}
		while (more);
		return objects;
	}

}
