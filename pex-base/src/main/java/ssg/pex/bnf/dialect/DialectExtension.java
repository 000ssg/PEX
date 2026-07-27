package ssg.pex.bnf.dialect;

import java.util.List;

public record DialectExtension(String dialectName,
                               String basedOn,
                               List<RuleModification> modifications) {

    public DialectExtension {
        modifications = List.copyOf(modifications);
    }
}
