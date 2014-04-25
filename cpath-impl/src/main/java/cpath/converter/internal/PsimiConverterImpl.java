package cpath.converter.internal;

import java.io.InputStream;
import java.io.OutputStream;

import org.biopax.paxtools.converter.psi.PsiToBiopax3Converter;

public final class PsimiConverterImpl extends BaseConverterImpl {

	public void convert(InputStream is, OutputStream os) {
		try {				
			PsiToBiopax3Converter psimiConverter = new PsiToBiopax3Converter(xmlBase);
			psimiConverter.convert(is, os, false);
			os.close();
		} catch (Exception e) {
			throw new RuntimeException("Failed to convert PSI-MI to BioPAX", e);
		}
	}

}
