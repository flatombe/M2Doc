/*******************************************************************************
 *  Copyright (c) 2016 Obeo. 
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *   
 *   Contributors:
 *       Obeo - initial API and implementation
 *  
 *******************************************************************************/
package org.obeonetwork.m2doc.parser;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.eclipse.acceleo.query.runtime.IQueryBuilderEngine;
import org.eclipse.acceleo.query.runtime.IQueryEnvironment;
import org.eclipse.acceleo.query.runtime.impl.QueryBuilderEngine;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.obeonetwork.m2doc.template.Block;
import org.obeonetwork.m2doc.template.Cell;
import org.obeonetwork.m2doc.template.ContentControl;
import org.obeonetwork.m2doc.template.IConstruct;
import org.obeonetwork.m2doc.template.Row;
import org.obeonetwork.m2doc.template.StaticFragment;
import org.obeonetwork.m2doc.template.Table;
import org.obeonetwork.m2doc.template.Template;
import org.obeonetwork.m2doc.template.TemplatePackage;
import org.obeonetwork.m2doc.util.FieldUtils;

import static org.obeonetwork.m2doc.util.FieldUtils.readUpInstrText;
import static org.obeonetwork.m2doc.util.M2DocUtils.message;

/**
 * Abstract Body Parser.
 * Common feature to parse template and m2doc result generated file (to find userdocdes tag).
 * 
 * @author ohaegi
 */
@SuppressWarnings("restriction")
public abstract class BodyAbstractParser {

    /**
     * Parsed template document.
     */
    protected IBody document;
    /**
     * iterator over the document used to access {@link XWPFRun} instances in
     * sequence.
     */
    protected TokenProvider runIterator;
    /**
     * {@link IQueryBuilderEngine} used to parse AQL queries.
     */
    protected IQueryBuilderEngine queryParser;

    /**
     * The {@link IQueryEnvironment}.
     */
    protected final IQueryEnvironment queryEnvironment;

    /**
     * The {@link FieldUtils}.
     */
    protected final FieldUtils fieldUtils;

    /**
     * Creates a new {@link M2DocParser} instance.
     * 
     * @param inputDocument
     *            the input template to parser
     * @param queryEnvironment
     *            the query environment to used during parsing.
     */
    public BodyAbstractParser(IBody inputDocument, IQueryEnvironment queryEnvironment) {
        this.document = inputDocument;
        runIterator = new TokenProvider(inputDocument);
        this.queryParser = new QueryBuilderEngine(queryEnvironment);
        this.queryEnvironment = queryEnvironment;
        this.fieldUtils = new FieldUtils();
    }

    /**
     * Creates a new {@link M2DocParser} instance.
     * 
     * @param inputDocument
     *            the input template to parser
     * @param queryParser
     *            the query parser to use during parsing
     * @param queryEnvironment
     *            The {@link IQueryEnvironment}
     */
    protected BodyAbstractParser(IBody inputDocument, IQueryBuilderEngine queryParser,
            IQueryEnvironment queryEnvironment) {
        this.document = inputDocument;
        runIterator = new TokenProvider(inputDocument);
        this.queryParser = queryParser;
        this.queryEnvironment = queryEnvironment;
        this.fieldUtils = new FieldUtils();
    }

    /**
     * returns the next token type after index.
     * 
     * @return the next token type.
     */
    protected abstract TokenType getNextTokenType();

    /**
     * Returns the template contained in the document.
     * 
     * @return the parsed template.
     * @throws DocumentParserException
     *             if a syntax problem is detected during parsing.
     */
    public Template parseTemplate() throws DocumentParserException {
        final Template template = (Template) EcoreUtil.create(TemplatePackage.Literals.TEMPLATE);
        template.setXWPFBody(this.document);
        final Block body = parseBlock(TokenType.EOF);
        template.setBody(body);

        return template;
    }

    /**
     * Parses a {@link Block}.
     * 
     * @param endTypes
     *            the token types that mark the end of the parsed compound
     * @return the parsed {@link Block}
     * @throws DocumentParserException
     *             if a problem occurs while parsing
     */
    protected abstract Block parseBlock(TokenType... endTypes) throws DocumentParserException;

    /**
     * Reads up a tag so that it can be parsed as a simple string.
     * 
     * @param construct
     *            the construct to read tag to
     * @param runsToFill
     *            the run list to fill
     * @return the string present into the tag as typed by the template author.
     */
    protected String readTag(IConstruct construct, List<XWPFRun> runsToFill) {
        XWPFRun run = this.runIterator.lookAhead(1).getRun();
        if (run == null) {
            throw new IllegalStateException("readTag shouldn't be called with a table in the lookahead window.");
        } else if (!fieldUtils.isFieldBegin(run)) {
            throw new IllegalStateException("Shouldn't call readTag if the current run doesn't start a field");
        }

        final StringBuilder result = new StringBuilder();

        runsToFill.add(runIterator.next().getRun()); // Consume begin field
        XWPFRun styleRun = null;
        boolean columnRead = false;
        while (runIterator.hasNext()) {
            run = runIterator.next().getRun();
            if (run == null) {
                // XXX : treat this as a proper parsing error.
                throw new IllegalArgumentException("table cannot be inserted into tags.");
            }
            runsToFill.add(run);
            if (fieldUtils.isFieldEnd(run)) {
                break;
            }
            final String runText = readUpInstrText(run).toString();
            result.append(runText);
            // the style run hasn't been discovered yet.
            if (styleRun == null) {
                if (columnRead && !runText.isEmpty()) {
                    styleRun = run;
                    construct.setStyleRun(styleRun);
                } else {
                    final int indexOfColumn = runText.indexOf(':');
                    columnRead = indexOfColumn >= 0;
                    if (columnRead && indexOfColumn < runText.length() - 1) {
                        styleRun = run; // ':' doesn't appear at the end of the string
                        construct.setStyleRun(styleRun);
                    } // otherwise, use the next non empty run.
                }
            }
        }

        return result.toString();
    }

    /**
     * Gets the {@link List} of {@link TemplateValidationMessage} from the given {@link Diagnostic}.
     * 
     * @param diagnostic
     *            the {@link Diagnostic}
     * @param queryText
     *            the query text
     * @param location
     *            the location of the {@link TemplateValidationMessage}
     * @return the {@link List} of {@link TemplateValidationMessage} from the given {@link Diagnostic}
     */
    protected List<TemplateValidationMessage> getValidationMessage(Diagnostic diagnostic, String queryText,
            XWPFRun location) {
        final List<TemplateValidationMessage> res = new ArrayList<TemplateValidationMessage>();

        for (Diagnostic child : diagnostic.getChildren()) {
            final ValidationMessageLevel level;
            switch (diagnostic.getSeverity()) {
                case Diagnostic.INFO:
                    level = ValidationMessageLevel.INFO;
                    break;

                case Diagnostic.WARNING:
                    level = ValidationMessageLevel.WARNING;
                    break;

                case Diagnostic.ERROR:
                    level = ValidationMessageLevel.ERROR;
                    break;

                default:
                    level = ValidationMessageLevel.INFO;
                    break;
            }
            res.add(new TemplateValidationMessage(level,
                    message(ParsingErrorMessage.INVALIDEXPR, queryText, child.getMessage()), location));
            res.addAll(getValidationMessage(child, queryText, location));
        }

        return res;
    }

    /**
     * Parses a {@link StaticFragment}.
     * 
     * @return the object created
     * @throws DocumentParserException
     *             if something wrong happens during parsing
     */
    protected StaticFragment parseStaticFragment() throws DocumentParserException {
        StaticFragment result = (StaticFragment) EcoreUtil.create(TemplatePackage.Literals.STATIC_FRAGMENT);
        while (getNextTokenType() == TokenType.STATIC) {
            result.getRuns().add(runIterator.next().getRun());
        }
        return result;
    }

    /**
     * Parses a {@link Table}.
     * 
     * @param wtable
     *            the table to parse
     * @return the created object
     * @throws DocumentParserException
     *             if a problem occurs while parsing.
     */
    protected Table parseTable(XWPFTable wtable) throws DocumentParserException {
        if (wtable == null) {
            throw new IllegalArgumentException("parseTable can't be called on a null argument.");
        }
        Table table = (Table) EcoreUtil.create(TemplatePackage.Literals.TABLE);
        table.setTable(wtable);
        for (XWPFTableRow tablerow : wtable.getRows()) {
            Row row = (Row) EcoreUtil.create(TemplatePackage.Literals.ROW);
            table.getRows().add(row);
            row.setTableRow(tablerow);
            for (XWPFTableCell tableCell : tablerow.getTableCells()) {
                Cell cell = (Cell) EcoreUtil.create(TemplatePackage.Literals.CELL);
                row.getCells().add(cell);
                cell.setTableCell(tableCell);
                BodyAbstractParser parser = getNewParser(tableCell);
                cell.setTemplate(parser.parseTemplate());
            }
        }
        return table;
    }

    /**
     * Parses a {@link ContentControl}.
     * 
     * @param control
     *            the {@link XWPFSDT}
     * @return the parsed {@link ContentControl}
     */
    protected ContentControl parseContentControl(XWPFSDT control) {
        if (control == null) {
            throw new IllegalArgumentException("parseContentControl can't be called on a null argument.");
        }
        ContentControl contentControl = (ContentControl) EcoreUtil.create(TemplatePackage.Literals.CONTENT_CONTROL);
        contentControl.setControl(control);

        return contentControl;
    }

    /**
     * Get new Parser.
     * 
     * @param inputDocument
     *            Document to parse
     * @return new Template parser
     */
    protected abstract BodyAbstractParser getNewParser(IBody inputDocument);

}
