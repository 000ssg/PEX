package ssg.pex.spi;

import ssg.pex.bnf.dialect.DialectExtension;
import ssg.pex.bnf.model.Grammar;

import java.util.List;

public interface GrammarProvider {

    String dialectName();

    default String basedOn() {
        return null;
    }

    Grammar provideGrammar();

    default List<DialectExtension> provideExtensions() {
        return List.of();
    }
}
