package ssg.pex.converter;

import ssg.pex.ast.node.AstNode;
import ssg.pex.result.Result;

public interface Converter {

    Result<String> convert(AstNode root);

    TargetLanguage targetLanguage();
}
