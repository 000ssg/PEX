package ssg.pex.converter.spi;

import ssg.pex.converter.ConversionConfig;
import ssg.pex.converter.Converter;
import ssg.pex.converter.TargetLanguage;

public interface ConverterFactory {

    TargetLanguage target();

    String description();

    Converter create(ConversionConfig config);
}
