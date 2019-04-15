/*
 * JasperReports - Free Java Reporting Library.
 * Copyright (C) 2001 - 2019 TIBCO Software Inc. All rights reserved.
 * http://www.jaspersoft.com
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is part of JasperReports.
 *
 * JasperReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JasperReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JasperReports. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.jasperreports.engine.export.ooxml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.font.TextAttribute;
import java.awt.geom.Dimension2D;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.annotations.properties.Property;
import net.sf.jasperreports.annotations.properties.PropertyScope;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRGenericElementType;
import net.sf.jasperreports.engine.JRGenericPrintElement;
import net.sf.jasperreports.engine.JRLineBox;
import net.sf.jasperreports.engine.JRPen;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintElementIndex;
import net.sf.jasperreports.engine.JRPrintEllipse;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintHyperlink;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintLine;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintGraphicElement;
import net.sf.jasperreports.engine.JRPrintRectangle;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JRRuntimeException;
import net.sf.jasperreports.engine.JRStyle;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.PrintPageFormat;
import net.sf.jasperreports.engine.base.JRBaseLineBox;
import net.sf.jasperreports.engine.export.CutsInfo;
import net.sf.jasperreports.engine.export.ElementGridCell;
import net.sf.jasperreports.engine.export.ExporterNature;
import net.sf.jasperreports.engine.export.GenericElementHandlerEnviroment;
import net.sf.jasperreports.engine.export.Grid;
import net.sf.jasperreports.engine.export.GridRow;
import net.sf.jasperreports.engine.export.HyperlinkUtil;
import net.sf.jasperreports.engine.export.JRExportProgressMonitor;
import net.sf.jasperreports.engine.export.JRExporterGridCell;
import net.sf.jasperreports.engine.export.JRGridLayout;
import net.sf.jasperreports.engine.export.JRHyperlinkProducer;
import net.sf.jasperreports.engine.export.JRXmlExporter;
import net.sf.jasperreports.engine.export.LengthUtil;
import net.sf.jasperreports.engine.export.OccupiedGridCell;
import net.sf.jasperreports.engine.export.zip.FileBufferedZipEntry;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.HyperlinkTypeEnum;
import net.sf.jasperreports.engine.type.LineDirectionEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.ScaleImageEnum;
import net.sf.jasperreports.engine.util.JRColorUtil;
import net.sf.jasperreports.engine.util.JRStringUtil;
import net.sf.jasperreports.engine.util.JRStyledText;
import net.sf.jasperreports.engine.util.JRTextAttribute;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.export.DocxExporterConfiguration;
import net.sf.jasperreports.export.DocxReportConfiguration;
import net.sf.jasperreports.export.ExportInterruptedException;
import net.sf.jasperreports.export.ExporterInputItem;
import net.sf.jasperreports.export.OutputStreamExporterOutput;
import net.sf.jasperreports.export.ReportExportConfiguration;
import net.sf.jasperreports.properties.PropertyConstants;
import net.sf.jasperreports.renderers.DataRenderable;
import net.sf.jasperreports.renderers.DimensionRenderable;
import net.sf.jasperreports.renderers.Renderable;
import net.sf.jasperreports.renderers.RenderersCache;
import net.sf.jasperreports.renderers.ResourceRenderer;


/**
 * Exports a JasperReports document to DOCX format. It has binary output type and exports the document to a
 * grid-based layout, therefore having the known limitations of grid exporters.
 * <p/>
 * It can work in batch mode and supports all types of
 * exporter input and output, content filtering, and font mappings.
 * <p/>
 * Currently, there are the following special configurations that can be made to a DOCX
 * exporter instance (see {@link net.sf.jasperreports.export.DocxReportConfiguration}):
 * <ul>
 * <li>Forcing the use of nested tables to render the content of frame elements using either
 * the {@link net.sf.jasperreports.export.DocxReportConfiguration#isFramesAsNestedTables() isFramesAsNestedTables()} 
 * exporter configuration flag or its corresponding exporter hint called
 * {@link net.sf.jasperreports.export.DocxReportConfiguration#PROPERTY_FRAMES_AS_NESTED_TABLES net.sf.jasperreports.export.docx.frames.as.nested.tables}.</li>
 * <li>Allowing table rows to adjust their height if more text is typed into their cells using
 * the Word editor. This is controlled using either the
 * {@link net.sf.jasperreports.export.DocxReportConfiguration#isFlexibleRowHeight() isFlexibleRowHeight()} 
 * exporter configuration flag, or its corresponding exporter hint called
 * {@link net.sf.jasperreports.export.DocxReportConfiguration#PROPERTY_FLEXIBLE_ROW_HEIGHT net.sf.jasperreports.export.docx.flexible.row.height}.</li>
 * <li>Ignoring hyperlinks in generated documents if they are not intended for the DOCX output format. This can be 
 * customized using either the 
 * {@link net.sf.jasperreports.export.DocxReportConfiguration#isIgnoreHyperlink() isIgnoreHyperlink()} 
 * exporter configuration flag, or its corresponding exporter hint called
 * {@link net.sf.jasperreports.export.DocxReportConfiguration#PROPERTY_IGNORE_HYPERLINK net.sf.jasperreports.export.docx.ignore.hyperlink}</li>
 * </ul>
 * 
 * @see net.sf.jasperreports.export.DocxReportConfiguration
 * @author Sanda Zaharia (shertage@users.sourceforge.net)
 */
public class JRDocxExporter extends JRAbstractExporter<DocxReportConfiguration, DocxExporterConfiguration, OutputStreamExporterOutput, JRDocxExporterContext>
{
	private static final Log log = LogFactory.getLog(JRDocxExporter.class);
	
	/**
	 * The exporter key, as used in
	 * {@link GenericElementHandlerEnviroment#getElementHandler(JRGenericElementType, String)}.
	 */
	public static final String DOCX_EXPORTER_KEY = JRPropertiesUtil.PROPERTY_PREFIX + "docx";
	
	public static final String EXCEPTION_MESSAGE_KEY_COLUMN_COUNT_OUT_OF_RANGE = "export.docx.column.count.out.of.range";
	
	public static final String DOCX_EXPORTER_PROPERTIES_PREFIX = JRPropertiesUtil.PROPERTY_PREFIX + "export.docx.";

	/**
	 * This property is used to mark text elements as being hidden either for printing or on-screen display.
	 * @see JRPropertiesUtil
	 */
	@Property(
			category = PropertyConstants.CATEGORY_EXPORT,
			defaultValue = PropertyConstants.BOOLEAN_FALSE,
			scopes = {PropertyScope.TEXT_ELEMENT},
			sinceVersion = PropertyConstants.VERSION_3_7_6,
			valueType = Boolean.class
			)
	public static final String PROPERTY_HIDDEN_TEXT = DOCX_EXPORTER_PROPERTIES_PREFIX + "hidden.text";

	/**
	 *
	 */
	public static final String JR_PAGE_ANCHOR_PREFIX = "JR_PAGE_ANCHOR_";

	/**
	 *
	 */
	public static final String IMAGE_NAME_PREFIX = "img_";
	protected static final int IMAGE_NAME_PREFIX_LEGTH = IMAGE_NAME_PREFIX.length();
	public static final String IMAGE_LINK_PREFIX = "link_" + IMAGE_NAME_PREFIX;
	
	/**
	 *
	 */
	protected DocxZip docxZip;
	protected DocxDocumentHelper docHelper;
	protected Writer docWriter;

	protected Map<String, String> rendererToImagePathMap;
	protected RenderersCache renderersCache;
//	protected Map imageMaps;

	protected int reportIndex;
	protected int pageIndex;
	protected int startPageIndex;
	protected int endPageIndex;
	protected int tableIndex;
	protected boolean startPage;
	protected String invalidCharReplacement;
	protected PrintPageFormat pageFormat;
	protected JRGridLayout pageGridLayout;

	protected LinkedList<Color> backcolorStack = new LinkedList<Color>();
	protected Color backcolor;

	protected DocxRunHelper runHelper;

	protected ExporterNature nature;

	protected long bookmarkIndex;
	
	protected String pageAnchor;
	
	protected DocxRelsHelper relsHelper;
	protected PropsAppHelper appHelper;
	protected PropsCoreHelper coreHelper;
	
	boolean emptyPageState;
	

	protected class ExporterContext extends BaseExporterContext implements JRDocxExporterContext
	{
		DocxTableHelper tableHelper = null;
		
		public ExporterContext(DocxTableHelper tableHelper)
		{
			this.tableHelper = tableHelper;
		}
		
		@Override
		public DocxTableHelper getTableHelper()
		{
			return tableHelper;
		}
	}
	
	
	/**
	 * @see #JRDocxExporter(JasperReportsContext)
	 */
	public JRDocxExporter()
	{
		this(DefaultJasperReportsContext.getInstance());
	}


	/**
	 *
	 */
	public JRDocxExporter(JasperReportsContext jasperReportsContext)
	{
		super(jasperReportsContext);
		
		exporterContext = new ExporterContext(null);
	}


	@Override
	protected Class<DocxExporterConfiguration> getConfigurationInterface()
	{
		return DocxExporterConfiguration.class;
	}


	@Override
	protected Class<DocxReportConfiguration> getItemConfigurationInterface()
	{
		return DocxReportConfiguration.class;
	}
	

	@Override
	@SuppressWarnings("deprecation")
	protected void ensureOutput()
	{
		if (exporterOutput == null)
		{
			exporterOutput = 
				new net.sf.jasperreports.export.parameters.ParametersOutputStreamExporterOutput(
					getJasperReportsContext(),
					getParameters(),
					getCurrentJasperPrint()
					);
		}
	}
	

	@Override
	public void exportReport() throws JRException
	{
		/*   */
		ensureJasperReportsContext();
		ensureInput();

		initExport();
		
		ensureOutput();
		
		OutputStream outputStream = getExporterOutput().getOutputStream();

		try
		{
			exportReportToStream(outputStream);
		}
		catch (IOException e)
		{
			throw new JRRuntimeException(e);
		}
		finally
		{
			getExporterOutput().close();
			resetExportContext();
		}
	}

	
	@Override
	protected void initExport()
	{
		super.initExport();

		rendererToImagePathMap = new HashMap<String,String>();//FIXMEIMAGE why this is reset at export and not report; are there any others?
//		imageMaps = new HashMap();
//		hyperlinksMap = new HashMap();
	}


	@Override
	protected void initReport()
	{
		super.initReport();
		
		if (jasperPrint.hasProperties() && jasperPrint.getPropertiesMap().containsProperty(JRXmlExporter.PROPERTY_REPLACE_INVALID_CHARS))
		{
			// allows null values for the property
			invalidCharReplacement = jasperPrint.getProperty(JRXmlExporter.PROPERTY_REPLACE_INVALID_CHARS);
		}
		else
		{
			invalidCharReplacement = getPropertiesUtil().getProperty(JRXmlExporter.PROPERTY_REPLACE_INVALID_CHARS, jasperPrint);
		}

		DocxReportConfiguration configuration = getCurrentItemConfiguration();
		
		nature = 
			new JRDocxExporterNature(
				jasperReportsContext, 
				filter, 
				!configuration.isFramesAsNestedTables()
				);

		renderersCache = new RenderersCache(getJasperReportsContext());
	}

	
	/**
	 *
	 */
	protected void exportReportToStream(OutputStream os) throws JRException, IOException
	{
		docxZip = new DocxZip();

		docWriter = docxZip.getDocumentEntry().getWriter();
		
		docHelper = new DocxDocumentHelper(jasperReportsContext, docWriter);
		docHelper.exportHeader();
		
		relsHelper = new DocxRelsHelper(jasperReportsContext, docxZip.getRelsEntry().getWriter());
		relsHelper.exportHeader();
		
		appHelper = new PropsAppHelper(jasperReportsContext, docxZip.getAppEntry().getWriter());
		coreHelper = new PropsCoreHelper(jasperReportsContext, docxZip.getCoreEntry().getWriter());

		appHelper.exportHeader();
			
		DocxExporterConfiguration configuration = getCurrentConfiguration();

		String application = configuration.getMetadataApplication();
		if( application == null )
		{
			application = "JasperReports Library version " + Package.getPackage("net.sf.jasperreports.engine").getImplementationVersion();
		}
		appHelper.exportProperty(PropsAppHelper.PROPERTY_APPLICATION, application);

		coreHelper.exportHeader();
		
		String title = configuration.getMetadataTitle();
		if (title != null)
		{
			coreHelper.exportProperty(PropsCoreHelper.PROPERTY_TITLE, title);
		}
		String subject = configuration.getMetadataSubject();
		if (subject != null)
		{
			coreHelper.exportProperty(PropsCoreHelper.PROPERTY_SUBJECT, subject);
		}
		String author = configuration.getMetadataAuthor();
		if (author != null)
		{
			coreHelper.exportProperty(PropsCoreHelper.PROPERTY_CREATOR, author);
		}
		String keywords = configuration.getMetadataKeywords();
		if (keywords != null)
		{
			coreHelper.exportProperty(PropsCoreHelper.PROPERTY_KEYWORDS, keywords);
		}

		List<ExporterInputItem> items = exporterInput.getItems();

		DocxStyleHelper styleHelper = 
			new DocxStyleHelper(
				this,
				docxZip.getStylesEntry().getWriter()
				);
		styleHelper.export(exporterInput);
		styleHelper.close();

		DocxSettingsHelper settingsHelper = 
			new DocxSettingsHelper(
				jasperReportsContext,
				docxZip.getSettingsEntry().getWriter()
				);
		settingsHelper.export(jasperPrint);
		settingsHelper.close();

		runHelper = new DocxRunHelper(jasperReportsContext, docWriter, getExporterKey());
		
		pageFormat = null;
		PrintPageFormat oldPageFormat = null;

		for(reportIndex = 0; reportIndex < items.size(); reportIndex++)
		{
			ExporterInputItem item = items.get(reportIndex);

			setCurrentExporterInputItem(item);

			bookmarkIndex = 0;
			emptyPageState = false;
			
			List<JRPrintPage> pages = jasperPrint.getPages();
			if (pages != null && pages.size() > 0)
			{
				PageRange pageRange = getPageRange();
				startPageIndex = (pageRange == null || pageRange.getStartPageIndex() == null) ? 0 : pageRange.getStartPageIndex();
				endPageIndex = (pageRange == null || pageRange.getEndPageIndex() == null) ? (pages.size() - 1) : pageRange.getEndPageIndex();

				JRPrintPage page = null;
				for(pageIndex = startPageIndex; pageIndex <= endPageIndex; pageIndex++)
				{
					if (Thread.interrupted())
					{
						throw new ExportInterruptedException();
					}

					page = pages.get(pageIndex);

					pageFormat = jasperPrint.getPageFormat(pageIndex);
					
					if (oldPageFormat != null && oldPageFormat != pageFormat)
					{
						docHelper.exportSection(oldPageFormat, pageGridLayout, false);
					}
					
					exportPage(page);

					oldPageFormat = pageFormat;
				}
			}
		}
		
		if (oldPageFormat != null)
		{
			docHelper.exportSection(oldPageFormat, pageGridLayout, true);
		}

		docHelper.exportFooter();
		docHelper.close();

//		if ((hyperlinksMap != null && hyperlinksMap.size() > 0))
//		{
//			for(Iterator it = hyperlinksMap.keySet().iterator(); it.hasNext();)
//			{
//				String href = (String)it.next();
//				String id = (String)hyperlinksMap.get(href);
//
//				relsHelper.exportHyperlink(id, href);
//			}
//		}

		relsHelper.exportFooter();
		relsHelper.close();

		appHelper.exportFooter();
		appHelper.close();

		coreHelper.exportFooter();
		coreHelper.close();

		docxZip.zipEntries(os);

		docxZip.dispose();
	}


	/**
	 *
	 */
	protected void exportPage(JRPrintPage page) throws JRException
	{
		startPage = true;
		pageAnchor = JR_PAGE_ANCHOR_PREFIX + reportIndex + "_" + (pageIndex + 1);
		
		ReportExportConfiguration configuration = getCurrentItemConfiguration();

		pageGridLayout =
			new JRGridLayout(
				nature,
				page.getElements(),
				pageFormat.getPageWidth(),
				pageFormat.getPageHeight(),
				configuration.getOffsetX() == null ? 0 : configuration.getOffsetX(), 
				configuration.getOffsetY() == null ? 0 : configuration.getOffsetY(),
				null //address
				);

		exportGrid(pageGridLayout, null);
		
		JRExportProgressMonitor progressMonitor = configuration.getProgressMonitor();
		if (progressMonitor != null)
		{
			progressMonitor.afterPageExport();
		}
	}


	/**
	 *
	 */
	protected void exportGrid(JRGridLayout gridLayout, JRPrintElementIndex frameIndex) throws JRException
	{
		
		CutsInfo xCuts = gridLayout.getXCuts();
		Grid grid = gridLayout.getGrid();
		DocxTableHelper tableHelper = null;

		int rowCount = grid.getRowCount();
		if (rowCount > 0 && grid.getColumnCount() > 63)
		{
			throw 
				new JRException(
					EXCEPTION_MESSAGE_KEY_COLUMN_COUNT_OUT_OF_RANGE,  
					new Object[]{grid.getColumnCount()} 
					);
		}
		
		// an empty page is encountered; 
		// if it's the first one in a series of consecutive empty pages, emptyPageState == false, otherwise emptyPageState == true
		if (rowCount == 0 && (pageIndex < endPageIndex || !emptyPageState))
		{
			tableHelper = 
					new DocxTableHelper(
						jasperReportsContext,
						docWriter, 
						xCuts,
						false,
						pageFormat,
						frameIndex
						);
			int maxReportIndex = exporterInput.getItems().size() - 1;
			
			// while the first and last page in the JasperPrint list need single breaks, all the others require double-breaking 
			boolean twice = 
					(pageIndex > startPageIndex && pageIndex < endPageIndex && !emptyPageState)
					||(reportIndex < maxReportIndex && pageIndex == endPageIndex);
			tableHelper.getParagraphHelper().exportEmptyPage(pageAnchor, bookmarkIndex, twice);
			bookmarkIndex++;
			emptyPageState = true;
			return;
		}
		
		tableHelper = 
				new DocxTableHelper(
					jasperReportsContext,
					docWriter, 
					xCuts,
					frameIndex == null && (reportIndex != 0 || pageIndex != startPageIndex),
					pageFormat,
					frameIndex
					);

		tableHelper.exportHeader();
		
		boolean isFlexibleRowHeight = getCurrentItemConfiguration().isFlexibleRowHeight();

		for(int row = 0; row < rowCount; row++)
		{
			int emptyCellColSpan = 0;
			//int emptyCellWidth = 0;

			boolean allowRowResize = false;
			int maxBottomPadding = 0; //for some strange reason, the bottom margin affects the row height; subtracting it here
			GridRow gridRow = grid.getRow(row);
			int rowSize = gridRow.size();
			for(int col = 0; col < rowSize; col++)
			{
				JRExporterGridCell gridCell = gridRow.get(col);
				JRLineBox box = gridCell.getBox();
				if (
					box != null 
					&& box.getBottomPadding() != null 
					&& maxBottomPadding < box.getBottomPadding()
					)
				{
					maxBottomPadding = box.getBottomPadding();
				}
				
				allowRowResize = 
					isFlexibleRowHeight 
					&& (allowRowResize 
						|| (gridCell.getElement() instanceof JRPrintText 
							|| (gridCell.getType() == JRExporterGridCell.TYPE_OCCUPIED_CELL
								&& ((OccupiedGridCell)gridCell).getOccupier().getElement() instanceof JRPrintText)
							)
						);
			}

			int rowHeight = gridLayout.getRowHeight(row) - maxBottomPadding;
			if (row == 0 && frameIndex == null)
			{
				rowHeight -= Math.min(rowHeight, pageFormat.getTopMargin());
			}

			tableHelper.exportRowHeader(
				rowHeight,
				allowRowResize
				);

			for(int col = 0; col < rowSize; col++)
			{
				JRExporterGridCell gridCell = gridRow.get(col);
				if (gridCell.getType() == JRExporterGridCell.TYPE_OCCUPIED_CELL)
				{
					if (emptyCellColSpan > 0)
					{
						//tableHelper.exportEmptyCell(gridCell, emptyCellColSpan);
						emptyCellColSpan = 0;
						//emptyCellWidth = 0;
					}

					OccupiedGridCell occupiedGridCell = (OccupiedGridCell)gridCell;
					ElementGridCell elementGridCell = (ElementGridCell)occupiedGridCell.getOccupier();
					tableHelper.exportOccupiedCells(elementGridCell, startPage, bookmarkIndex, pageAnchor);
					if (startPage)
					{
						// increment the bookmarkIndex for the first cell in the sheet, due to page anchor creation
						bookmarkIndex++;
					}
					col += elementGridCell.getColSpan() - 1;
				}
				else if (gridCell.getType() == JRExporterGridCell.TYPE_ELEMENT_CELL)
				{
					if (emptyCellColSpan > 0)
					{
						//writeEmptyCell(tableHelper, gridCell, emptyCellColSpan, emptyCellWidth, rowHeight);
						emptyCellColSpan = 0;
						//emptyCellWidth = 0;
					}

					JRPrintElement element = gridCell.getElement();

					if (element instanceof JRPrintLine)
					{
						exportLine(tableHelper, (JRPrintLine)element, gridCell);
					}
					else if (element instanceof JRPrintRectangle)
					{
						exportRectangle(tableHelper, (JRPrintRectangle)element, gridCell);
					}
					else if (element instanceof JRPrintEllipse)
					{
						exportEllipse(tableHelper, (JRPrintEllipse)element, gridCell);
					}
					else if (element instanceof JRPrintImage)
					{
						exportImage(tableHelper, (JRPrintImage)element, gridCell);
					}
					else if (element instanceof JRPrintText)
					{
						exportText(tableHelper, (JRPrintText)element, gridCell);
					}
					else if (element instanceof JRPrintFrame)
					{
						exportFrame(tableHelper, (JRPrintFrame)element, gridCell);
					}
					else if (element instanceof JRGenericPrintElement)
					{
						exportGenericElement(tableHelper, (JRGenericPrintElement)element, gridCell);
					}

					col += gridCell.getColSpan() - 1;
				}
				else
				{
					emptyCellColSpan++;
					//emptyCellWidth += gridCell.getWidth();
					tableHelper.exportEmptyCell(gridCell, 1, startPage, bookmarkIndex, pageAnchor);
					if (startPage)
					{
						// increment the bookmarkIndex for the first cell in the sheet, due to page anchor creation
						bookmarkIndex++;
					}
				}
				startPage = false;
			}

//			if (emptyCellColSpan > 0)
//			{
//				//writeEmptyCell(tableHelper, null, emptyCellColSpan, emptyCellWidth, rowHeight);
//			}

			tableHelper.exportRowFooter();
		}

		tableHelper.exportFooter();
		// if a non-empty page was exported, the series of previous empty pages is ended
		emptyPageState = false;
	}

	private void drawShape(JRPrintGraphicElement shape, JRExporterGridCell gridCell) throws JRException
	{
		String shapeType = "rect";
		String flip = "";
		String radius = "<a:avLst></a:avLst>";

		if (shape instanceof JRPrintEllipse)
		{
			shapeType = "ellipse";

		}
		else if (shape instanceof JRPrintLine)
		{
			shapeType = "line";
			if (((JRPrintLine)shape).getDirectionValue() != LineDirectionEnum.TOP_DOWN)
			{
				flip = " flipV=\"1\"";
			}
		}
		else if (shape instanceof JRPrintRectangle)
		{
			shapeType = (((JRPrintRectangle)shape).getRadius() == 0) ? "rect" : "roundRect";
			if (((JRPrintRectangle)shape).getRadius() > 0)
			{
				// a rounded rectangle radius cannot exceed 1/2 of its lower side;
				int size = Math.min(50000, (((JRPrintRectangle)shape).getRadius() * 100000)/Math.min(shape.getHeight(), shape.getWidth()));
				radius = "<a:avLst><a:gd name=\"adj\" fmla=\"val "+ size +"\"/></a:avLst>";
			}
		}
		else
		{
			shapeType = "rect";
		}

		String bgColor = "";
		if (shape.getModeValue() == ModeEnum.OPAQUE && shape.getBackcolor() != null)
		{
			bgColor = "<a:solidFill><a:srgbClr val=\"" + JRColorUtil.getColorHexa(shape.getBackcolor()) + "\"/></a:solidFill>";
		}

		JRPen pen = shape.getLinePen();
		String fgColor = "<a:solidFill><a:srgbClr val=\"" + JRColorUtil.getColorHexa(pen.getLineColor()) + "\"/></a:solidFill>";
		String penStyle = "";
		if (pen.getLineWidth() > 0)
		{
			switch (pen.getLineStyleValue())
			{
				case DASHED :
				{
					penStyle = "<a:custDash><a:ds d=\"800000\" sp=\"800000\"/></a:custDash>";
					break;
				}
				case DOTTED :
				{
					penStyle = "<a:custDash><a:ds d=\"100000\" sp=\"100000\"/></a:custDash>";
					break;
				}
				case DOUBLE :
				case SOLID :
				default :
				{
					break;
				}
			}
		}


		try
		{
			docWriter.write(
				"<w:tc>"
					+ "<w:tcPr>"
						+ "<w:gridSpan w:val=\"" + gridCell.getColSpan() + "\" />"
						+ "<w:tcBorders></w:tcBorders>"
						+ "<w:shd w:fill=\"auto\" w:val=\"clear\"/>"
						+ "<w:tcMar>"
						   + "<w:top w:w=\"0\" w:type=\"dxa\" />"
						   + "<w:left w:w=\"0\" w:type=\"dxa\" />"
						   + "<w:bottom w:w=\"0\" w:type=\"dxa\" />"
						   + "<w:right w:w=\"0\" w:type=\"dxa\" />"
						+ "</w:tcMar>"
					+ "</w:tcPr>"
					+ "<w:p>"
						+ "<w:r><w:rPr></w:rPr>"
							+ "<mc:AlternateContent>"
								+ "<mc:Choice Requires=\"wps\">"
									+ "<w:drawing>"
										+ "<wp:anchor behindDoc=\"0\" distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" locked=\"0\" layoutInCell=\"1\" allowOverlap=\"1\" relativeHeight=\"2\">"
										+ "<wp:simplePos x=\"0\" y=\"0\"/>"
						 + "<wp:positionH relativeFrom=\"page\">"
						+ "<wp:posOffset>" + LengthUtil.emu(shape.getX()) + "</wp:posOffset>"
										+ "</wp:positionH>"
										+ "<wp:positionV relativeFrom=\"page\">"
											+ "<wp:posOffset>" + LengthUtil.emu(shape.getY()) + "</wp:posOffset>"
										+ "</wp:positionV>"
						+ "<wp:extent cx=\"" + LengthUtil.emu(shape.getWidth()) + "\" cy=\"" + LengthUtil.emu(shape.getHeight()) + "\"/>"
										+ "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
										+ "<wp:wrapNone/>"
										+ "<wp:docPr id=\"1\" name=\"shape1\"/>"
										+ "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
											+ "<a:graphicData uri=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\">"
												+ "<wps:wsp>"
												    + "<wps:cNvSpPr/>"
												    + "<wps:spPr>"
												        + "<a:xfrm" + flip + ">"
												            + "<a:off x=\"0\" y=\"0\"/>"
															+ "<a:ext cx=\"" + LengthUtil.emu(shape.getWidth()) + "\" cy=\"" + LengthUtil.emu(shape.getHeight()) + "\"/>"
                                						+ "</a:xfrm>"
                                						+ "<a:prstGeom prst=\"" + shapeType + "\">"
															+ radius
												        + "</a:prstGeom>"
														+ bgColor
														+ "<a:ln w=\"" + LengthUtil.emu(Math.max(Math.round(pen.getLineWidth()), 0)) + "\">"
														+ fgColor
														+ penStyle
												        + "</a:ln>"
												    + "</wps:spPr>"
												    + "<wps:style>"
												        + "<a:lnRef idx=\"0\"/>"
												        + "<a:fillRef idx=\"0\"/>"
												        + "<a:effectRef idx=\"0\"/>"
												        + "<a:fontRef idx=\"minor\"/>"
												    + "</wps:style>"
												    + "<wps:bodyPr/>"
												+ "</wps:wsp>"
											+ "</a:graphicData>"
										+ "</a:graphic>"
									+ "</wp:anchor>"
								+ "</w:drawing>"
							+ "</mc:Choice>"
							+ "<mc:Fallback>"
						+ "<w:pict>"
							+ "<v:oval id=\"shape_0\" ID=\"shape1\" fillcolor=\"#729fcf\" stroked=\"t\" style=\"position:absolute;margin-left:4.7pt;margin-top:6.35pt;width:93.7pt;height:42.7pt\">"
								+ "<w10:wrap type=\"none\"/>"
								+ "<v:fill o:detectmouseclick=\"t\" color2=\"#8d6030\"/>"
								+ "<v:stroke color=\"#3465a4\" joinstyle=\"round\" endcap=\"flat\"/>"
							+ "</v:oval>"
						+ "</w:pict>"
					+ "</mc:Fallback>"
				+ "</mc:AlternateContent>"
			+ "</w:r></w:p></w:tc>"
		);
		} catch (IOException e) {
			throw new JRException(e);
		}
	}

	/**
	 *
	 */
	protected void exportLine(DocxTableHelper tableHelper, JRPrintLine line, JRExporterGridCell gridCell) throws JRException
	{
		drawShape(line, gridCell);
	}


	/**
	 *
	 */
	protected void exportRectangle(DocxTableHelper tableHelper, JRPrintRectangle rectangle, JRExporterGridCell gridCell) throws JRException
	{
		drawShape(rectangle, gridCell);
	}


	/**
	 *
	 */
	protected void exportEllipse(DocxTableHelper tableHelper, JRPrintEllipse ellipse, JRExporterGridCell gridCell) throws JRException
	{
		drawShape(ellipse, gridCell);
	}


	/**
	 *
	 */
	public void exportText(DocxTableHelper tableHelper, JRPrintText text, JRExporterGridCell gridCell)
	{
		tableHelper.getCellHelper().exportHeader(text, gridCell);

		JRStyledText styledText = getStyledText(text);

		int textLength = 0;

		if (styledText != null)
		{
			textLength = styledText.length();
		}

//		if (styleBuffer.length() > 0)
//		{
//			writer.write(" style=\"");
//			writer.write(styleBuffer.toString());
//			writer.write("\"");
//		}
//
//		writer.write(">");
		docHelper.write("     <w:p>\n");

		tableHelper.getParagraphHelper().exportProps(text);
		if (startPage)
		{
			insertBookmark(pageAnchor, docHelper);
		}
		if (text.getAnchorName() != null)
		{
			insertBookmark(text.getAnchorName(), docHelper);
		}

		boolean startedHyperlink = startHyperlink(text, true);
		boolean isNewLineAsParagraph = false;
		if (HorizontalTextAlignEnum.JUSTIFIED.equals(text.getHorizontalTextAlign()))
		{
			if (text.hasProperties() && text.getPropertiesMap().containsProperty(DocxReportConfiguration.PROPERTY_NEW_LINE_AS_PARAGRAPH))
			{
				isNewLineAsParagraph = getPropertiesUtil().getBooleanProperty(text, DocxReportConfiguration.PROPERTY_NEW_LINE_AS_PARAGRAPH, false);
			}
			else
			{
				isNewLineAsParagraph = getCurrentItemConfiguration().isNewLineAsParagraph();
			}
		}

		if (textLength > 0)
		{
			exportStyledText(
				getCurrentJasperPrint().getDefaultStyleProvider().getStyleResolver().getBaseStyle(text), 
				styledText, 
				getTextLocale(text),
				getPropertiesUtil().getBooleanProperty(text, PROPERTY_HIDDEN_TEXT, false),
				startedHyperlink, 
				isNewLineAsParagraph
				);
		}

		if (startedHyperlink)
		{
			endHyperlink(true);
		}

		docHelper.write("     </w:p>\n");

		tableHelper.getCellHelper().exportFooter();
	}


	/**
	 *
	 */
	protected void exportStyledText(JRStyle style, JRStyledText styledText, Locale locale, boolean hiddenText, boolean startedHyperlink, boolean isNewLineJustified)
	{
		Color elementBackcolor = null;
		Map<AttributedCharacterIterator.Attribute, Object> globalAttributes = styledText.getGlobalAttributes();
		if (globalAttributes != null)
		{
			elementBackcolor = (Color)styledText.getGlobalAttributes().get(TextAttribute.BACKGROUND);
		}
		
		String text = styledText.getText();

		int runLimit = 0;

		AttributedCharacterIterator iterator = styledText.getAttributedString().getIterator();

		while(runLimit < styledText.length() && (runLimit = iterator.getRunLimit()) <= styledText.length())
		{
			Map<Attribute,Object> attributes = iterator.getAttributes();
			
			boolean localHyperlink = false;

			if (!startedHyperlink)
			{
				JRPrintHyperlink hyperlink = (JRPrintHyperlink)attributes.get(JRTextAttribute.HYPERLINK);
				if (hyperlink != null)
				{
					localHyperlink = startHyperlink(hyperlink, true);
				}
			}
			
			runHelper.export(
				style, 
				iterator.getAttributes(), 
				text.substring(iterator.getIndex(), runLimit),
				locale,
				hiddenText,
				invalidCharReplacement,
				elementBackcolor,
				isNewLineJustified
				);
			
			if (localHyperlink)
			{
				endHyperlink(true);
			}

			iterator.setIndex(runLimit);
		}
	}


	/**
	 *
	 */
	public void exportImage(DocxTableHelper tableHelper, JRPrintImage image, JRExporterGridCell gridCell) throws JRException
	{
		int leftPadding = image.getLineBox().getLeftPadding();
		int topPadding = image.getLineBox().getTopPadding();//FIXMEDOCX maybe consider border thickness
		int rightPadding = image.getLineBox().getRightPadding();
		int bottomPadding = image.getLineBox().getBottomPadding();

		int availableImageWidth = image.getWidth() - leftPadding - rightPadding;
		availableImageWidth = availableImageWidth < 0 ? 0 : availableImageWidth;

		int availableImageHeight = image.getHeight() - topPadding - bottomPadding;
		availableImageHeight = availableImageHeight < 0 ? 0 : availableImageHeight;

		tableHelper.getCellHelper().exportHeader(image, gridCell);

		docHelper.write("<w:p>\n");//FIXMEDOCX why is this here and not further down?
		tableHelper.getParagraphHelper().exportProps(image);

		Renderable renderer = image.getRenderer();

		if (
			renderer != null
			&& availableImageWidth > 0
			&& availableImageHeight > 0
			)
		{
			InternalImageProcessor imageProcessor = 
				new InternalImageProcessor(
					image, 
					image.getScaleImageValue() != ScaleImageEnum.FILL_FRAME, 
					gridCell,
					availableImageWidth,
					availableImageHeight
					);
				
			InternalImageProcessorResult imageProcessorResult = null;
			
			try
			{
				imageProcessorResult = imageProcessor.process(renderer);
			}
			catch (Exception e)
			{
				Renderable onErrorRenderer = getRendererUtil().handleImageError(e, image.getOnErrorTypeValue());
				if (onErrorRenderer != null)
				{
					imageProcessorResult = imageProcessor.process(onErrorRenderer);
				}
			}
			
			if (imageProcessorResult != null)
			{
				int width = availableImageWidth;
				int height = availableImageHeight;

				double cropTop = 0;
				double cropLeft = 0;
				double cropBottom = 0;
				double cropRight = 0;
				
				switch (image.getScaleImageValue())
				{
					case FILL_FRAME :
					{
						width = availableImageWidth;
						height = availableImageHeight;
						break;
					}
					case CLIP :
					{
						double normalWidth = availableImageWidth;
						double normalHeight = availableImageHeight;

						Dimension2D dimension = imageProcessorResult.dimension;
						if (dimension != null)
						{
							normalWidth = dimension.getWidth();
							normalHeight = dimension.getHeight();
						}

						if (normalWidth > availableImageWidth)
						{
							switch (image.getHorizontalImageAlign())
							{
								case RIGHT :
								{
									cropLeft = 65536 * (normalWidth - availableImageWidth) / normalWidth;
									cropRight = 0;
									break;
								}
								case CENTER :
								{
									cropLeft = 65536 * (- availableImageWidth + normalWidth) / normalWidth / 2;
									cropRight = cropLeft;
									break;
								}
								case LEFT :
								default :
								{
									cropLeft = 0;
									cropRight = 65536 * (normalWidth - availableImageWidth) / normalWidth;
									break;
								}
							}
							width = availableImageWidth;
							cropLeft = cropLeft / 0.75d;
							cropRight = cropRight / 0.75d;
						}
						else
						{
							width = (int)normalWidth;
						}

						if (normalHeight > availableImageHeight)
						{
							switch (image.getVerticalImageAlign())
							{
								case TOP :
								{
									cropTop = 0;
									cropBottom = 65536 * (normalHeight - availableImageHeight) / normalHeight;
									break;
								}
								case MIDDLE :
								{
									cropTop = 65536 * (normalHeight - availableImageHeight) / normalHeight / 2;
									cropBottom = cropTop;
									break;
								}
								case BOTTOM :
								default :
								{
									cropTop = 65536 * (normalHeight - availableImageHeight) / normalHeight;
									cropBottom = 0;
									break;
								}
							}
							height = availableImageHeight;
							cropTop = cropTop / 0.75d;
							cropBottom = cropBottom / 0.75d;
						}
						else
						{
							height = (int)normalHeight;
						}

						break;
					}
					case RETAIN_SHAPE :
					default :
					{
						double normalWidth = availableImageWidth;
						double normalHeight = availableImageHeight;

						Dimension2D dimension = imageProcessorResult.dimension;
						if (dimension != null)
						{
							normalWidth = dimension.getWidth();
							normalHeight = dimension.getHeight();
						}

						double ratio = normalWidth / normalHeight;

						if (ratio > availableImageWidth / (double)availableImageHeight)
						{
							width = availableImageWidth;
							height = (int)(width/ratio);

						}
						else
						{
							height = availableImageHeight;
							width = (int)(ratio * height);
						}
					}
				}

				if (startPage)
				{
					insertBookmark(pageAnchor, docHelper);
				}
				if (image.getAnchorName() != null)
				{
					insertBookmark(image.getAnchorName(), docHelper);
				}


//				boolean startedHyperlink = startHyperlink(image,false);

				docHelper.write("<w:r>\n"); 
				docHelper.write("<w:rPr/>\n"); 
				docHelper.write("<w:drawing>\n");
				docHelper.write("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">\n");
				docHelper.write("<wp:extent cx=\"" + LengthUtil.emu(width) + "\" cy=\"" + LengthUtil.emu(height) + "\"/>\n");
				docHelper.write("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>\n");

				int imageId = image.hashCode() > 0 ? image.hashCode() : -image.hashCode();
				String rId = IMAGE_LINK_PREFIX + getElementIndex(gridCell);
				docHelper.write("<wp:docPr id=\"" + imageId + "\" name=\"Picture\">\n");
				if (getHyperlinkURL(image) != null)
				{
					docHelper.write("<a:hlinkClick xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" r:id=\"" + rId + "\"/>\n");
				}
				docHelper.write("</wp:docPr>\n");
				docHelper.write("<a:graphic>\n");
				docHelper.write("<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n");
				docHelper.write("<pic:pic>\n");
				docHelper.write("<pic:nvPicPr><pic:cNvPr id=\"" + imageId + "\" name=\"Picture\"/><pic:cNvPicPr/></pic:nvPicPr>\n");
				docHelper.write("<pic:blipFill>\n");

				docHelper.write("<a:blip r:embed=\"" + imageProcessorResult.imagePath + "\"/>");
				docHelper.write("<a:srcRect");
				if (cropLeft > 0)
				{
					docHelper.write(" l=\"" + (int)cropLeft + "\"");
				}
				if (cropTop > 0)
				{
					docHelper.write(" t=\"" + (int)cropTop + "\"");
				}
				if (cropRight > 0)
				{
					docHelper.write(" r=\"" + (int)cropRight + "\"");
				}
				if (cropBottom > 0)
				{
					docHelper.write(" b=\"" + (int)cropBottom + "\"");
				}
				docHelper.write("/>");
				docHelper.write("<a:stretch><a:fillRect/></a:stretch>\n");
				docHelper.write("</pic:blipFill>\n");
				docHelper.write("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + LengthUtil.emu(width) + "\" cy=\"" + LengthUtil.emu(height) + "\"/>");
				docHelper.write("</a:xfrm><a:prstGeom prst=\"rect\"></a:prstGeom></pic:spPr>\n");
				docHelper.write("</pic:pic>\n");
				docHelper.write("</a:graphicData>\n");
				docHelper.write("</a:graphic>\n");
				docHelper.write("</wp:inline>\n");
				docHelper.write("</w:drawing>\n");
				docHelper.write("</w:r>"); 

				String url =  getHyperlinkURL(image);

				if (url != null)
				{
					String targetMode = "";
					switch(image.getHyperlinkTypeValue())
					{
						case LOCAL_PAGE:
						case LOCAL_ANCHOR:
						{
							relsHelper.exportImageLink(rId, "#"+url.replaceAll("\\W", ""), targetMode);
							break;
						}
						
						case REMOTE_PAGE:
						case REMOTE_ANCHOR:
						case REFERENCE:
						{
							targetMode = " TargetMode=\"External\"";
							relsHelper.exportImageLink(rId, url, targetMode);
							break;
						}
						default:
						{
							break;
						}
					}
				}
				
//				if (startedHyperlink)
//				{
//					endHyperlink(false);
//				}
			}
		}

		docHelper.write("</w:p>");

		tableHelper.getCellHelper().exportFooter();
	}

	private class InternalImageProcessor
	{
		private final JRPrintElement imageElement;
		private final RenderersCache imageRenderersCache;
		private final boolean needDimension; 
		private final JRExporterGridCell cell;
		private final int availableImageWidth;
		private final int availableImageHeight;

		protected InternalImageProcessor(
			JRPrintImage imageElement,
			boolean needDimension, 
			JRExporterGridCell cell,
			int availableImageWidth,
			int availableImageHeight
			)
		{
			this.imageElement = imageElement;
			this.imageRenderersCache = imageElement.isUsingCache() ? renderersCache : new RenderersCache(getJasperReportsContext());
			this.needDimension = needDimension;
			this.cell = cell;
			this.availableImageWidth = availableImageWidth;
			this.availableImageHeight = availableImageHeight;
		}
		
		private InternalImageProcessorResult process(Renderable renderer) throws JRException
		{
			if (renderer instanceof ResourceRenderer)
			{
				renderer = imageRenderersCache.getLoadedRenderer((ResourceRenderer)renderer);
			}
			
			// check dimension first, to avoid caching renderers that might not be used eventually, due to their dimension errors 
			Dimension2D dimension = null;
			if (needDimension)
			{
				DimensionRenderable dimensionRenderer = imageRenderersCache.getDimensionRenderable(renderer);
				dimension = dimensionRenderer == null ? null :  dimensionRenderer.getDimension(jasperReportsContext);
			}
			
			
			String imagePath = null;

//			if (image.isLazy()) //FIXMEDOCX learn how to link images				
//			{
//
//			}
//			else
//			{
				if (
					renderer instanceof DataRenderable //we do not cache imagePath for non-data renderers because they render width different width/height each time
					&& rendererToImagePathMap.containsKey(renderer.getId())
					)
				{
					imagePath = rendererToImagePathMap.get(renderer.getId());
				}
				else
				{
					JRPrintElementIndex imageIndex = getElementIndex(cell);

					DataRenderable imageRenderer = 
						getRendererUtil().getImageDataRenderable(
							imageRenderersCache,
							renderer,
							new Dimension(availableImageWidth, availableImageHeight),
							ModeEnum.OPAQUE == imageElement.getModeValue() ? imageElement.getBackcolor() : null
							);

					byte[] imageData = imageRenderer.getData(jasperReportsContext);
					String fileExtension = JRTypeSniffer.getImageTypeValue(imageData).getFileExtension();
					String imageName = IMAGE_NAME_PREFIX + imageIndex.toString() + (fileExtension == null ? "" : ("." + fileExtension));
					
					docxZip.addEntry(//FIXMEDOCX optimize with a different implementation of entry
						new FileBufferedZipEntry(
							"word/media/" + imageName,
							imageData
							)
						);
					
					relsHelper.exportImage(imageName);

					imagePath = imageName;
					//imagePath = "Pictures/" + imageName;

					if (imageRenderer == renderer)
					{
						//cache imagePath only for true ImageRenderable instances because the wrapping ones render with different width/height each time
						rendererToImagePathMap.put(renderer.getId(), imagePath);
					}
				}
//			}

			return new InternalImageProcessorResult(imagePath, dimension);
		}
	}

	private class InternalImageProcessorResult
	{
		protected final String imagePath;
		protected final Dimension2D dimension;
		
		protected InternalImageProcessorResult(String imagePath, Dimension2D dimension)
		{
			this.imagePath = imagePath;
			this.dimension = dimension;
		}
	}

	protected JRPrintElementIndex getElementIndex(JRExporterGridCell gridCell)
	{
		JRPrintElementIndex imageIndex =
			new JRPrintElementIndex(
					reportIndex,
					pageIndex,
					gridCell.getElementAddress()
					);
		return imageIndex;
	}


	/**
	 *
	 *
	protected void writeImageMap(String imageMapName, JRPrintHyperlink mainHyperlink, List imageMapAreas)
	{
		writer.write("<map name=\"" + imageMapName + "\">\n");

		for (Iterator it = imageMapAreas.iterator(); it.hasNext();)
		{
			JRPrintImageAreaHyperlink areaHyperlink = (JRPrintImageAreaHyperlink) it.next();
			JRPrintImageArea area = areaHyperlink.getArea();

			writer.write("  <area shape=\"" + JRPrintImageArea.getHtmlShape(area.getShape()) + "\"");
			writeImageAreaCoordinates(area);
			writeImageAreaHyperlink(areaHyperlink.getHyperlink());
			writer.write("/>\n");
		}

		if (mainHyperlink.getHyperlinkTypeValue() != NONE)
		{
			writer.write("  <area shape=\"default\"");
			writeImageAreaHyperlink(mainHyperlink);
			writer.write("/>\n");
		}

		writer.write("</map>\n");
	}


	protected void writeImageAreaCoordinates(JRPrintImageArea area)
	{
		int[] coords = area.getCoordinates();
		if (coords != null && coords.length > 0)
		{
			StringBuilder coordsEnum = new StringBuilder(coords.length * 4);
			coordsEnum.append(coords[0]);
			for (int i = 1; i < coords.length; i++)
			{
				coordsEnum.append(',');
				coordsEnum.append(coords[i]);
			}

			writer.write(" coords=\"" + coordsEnum + "\"");
		}
	}


	protected void writeImageAreaHyperlink(JRPrintHyperlink hyperlink)
	{
		String href = getHyperlinkURL(hyperlink);
		if (href == null)
		{
			writer.write(" nohref=\"nohref\"");
		}
		else
		{
			writer.write(" href=\"" + href + "\"");

			String target = getHyperlinkTarget(hyperlink);
			if (target != null)
			{
				writer.write(" target=\"");
				writer.write(target);
				writer.write("\"");
			}
		}

		if (hyperlink.getHyperlinkTooltip() != null)
		{
			writer.write(" title=\"");
			writer.write(JRStringUtil.xmlEncode(hyperlink.getHyperlinkTooltip()));
			writer.write("\"");
		}
	}


	/**
	 *
	 */
	public static JRPrintElementIndex getPrintElementIndex(String imageName)
	{
		if (!imageName.startsWith(IMAGE_NAME_PREFIX))
		{
			throw 
				new JRRuntimeException(
					EXCEPTION_MESSAGE_KEY_INVALID_IMAGE_NAME,
					new Object[]{imageName});
		}

		return JRPrintElementIndex.parsePrintElementIndex(imageName.substring(IMAGE_NAME_PREFIX_LEGTH));
	}


	/**
	 * In deep grids, this is called only for empty frames.
	 */
	protected void exportFrame(DocxTableHelper tableHelper, JRPrintFrame frame, JRExporterGridCell gridCell) throws JRException
	{
		tableHelper.getCellHelper().exportHeader(frame, gridCell);
//		tableHelper.getCellHelper().exportProps(gridCell);

		boolean appendBackcolor =
			frame.getModeValue() == ModeEnum.OPAQUE
			&& (backcolor == null || frame.getBackcolor().getRGB() != backcolor.getRGB());

		if (appendBackcolor)
		{
			setBackcolor(frame.getBackcolor());
		}

		try
		{
			JRGridLayout layout = ((ElementGridCell) gridCell).getLayout();
			JRPrintElementIndex frameIndex =
				new JRPrintElementIndex(
						reportIndex,
						pageIndex,
						gridCell.getElementAddress()
						);
			exportGrid(layout, frameIndex);
		}
		finally
		{
			if (appendBackcolor)
			{
				restoreBackcolor();
			}
		}
		
		tableHelper.getParagraphHelper().exportEmptyParagraph();
		tableHelper.getCellHelper().exportFooter();
	}


	/**
	 *
	 */
	protected void exportGenericElement(DocxTableHelper tableHelper, JRGenericPrintElement element, JRExporterGridCell gridCell)
	{
		GenericElementDocxHandler handler = (GenericElementDocxHandler) 
		GenericElementHandlerEnviroment.getInstance(getJasperReportsContext()).getElementHandler(
				element.getGenericType(), DOCX_EXPORTER_KEY);

		if (handler != null)
		{
			JRDocxExporterContext exporterContext = new ExporterContext(tableHelper);

			handler.exportElement(exporterContext, element, gridCell);
		}
		else
		{
			if (log.isDebugEnabled())
			{
				log.debug("No DOCX generic element handler for " 
						+ element.getGenericType());
			}
		}
	}


	/**
	 *
	 */
	protected void setBackcolor(Color color)
	{
		backcolorStack.addLast(backcolor);

		backcolor = color;
	}


	protected void restoreBackcolor()
	{
		backcolor = backcolorStack.removeLast();
	}


	protected boolean startHyperlink(JRPrintHyperlink link, boolean isText)
	{
		String href = getHyperlinkURL(link);

		if (href != null)
		{
//			String id = (String)hyperlinksMap.get(href);
//			if (id == null)
//			{
//				id = "link" + hyperlinksMap.size();
//				hyperlinksMap.put(href, id);
//			}
//			
//			docHelper.write("<w:hyperlink r:id=\"" + id + "\"");
//
//			String target = getHyperlinkTarget(link);//FIXMETARGET
//			if (target != null)
//			{
//				docHelper.write(" tgtFrame=\"" + target + "\"");
//			}
//
//			docHelper.write(">\n");

			docHelper.write("<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>\n");
			String localType = (HyperlinkTypeEnum.LOCAL_ANCHOR == link.getHyperlinkTypeValue() || 
					HyperlinkTypeEnum.LOCAL_PAGE == link.getHyperlinkTypeValue()) ? "\\l " : "";
					
			docHelper.write("<w:r><w:instrText xml:space=\"preserve\"> HYPERLINK " + localType +"\"" + JRStringUtil.xmlEncode(href,invalidCharReplacement) + "\"");

			String target = getHyperlinkTarget(link);//FIXMETARGET
			if (target != null)
			{
				docHelper.write(" \\t \"" + target + "\"");
			}

			String tooltip = link.getHyperlinkTooltip(); 
			if (tooltip != null)
			{
				docHelper.write(" \\o \"" + JRStringUtil.xmlEncode(tooltip, invalidCharReplacement) + "\"");
			}

			docHelper.write(" </w:instrText></w:r>\n");
			docHelper.write("<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>\n");
		}

		return href != null;
	}


	protected String getHyperlinkTarget(JRPrintHyperlink link)
	{
		String target = null;
		switch(link.getHyperlinkTargetValue())
		{
			case SELF :
			{
				target = "_self";
				break;
			}
			case BLANK :
			default :
			{
				target = "_blank";
				break;
			}
		}
		return target;
	}


	protected String getHyperlinkURL(JRPrintHyperlink link)
	{
		String href = null;

		Boolean ignoreHyperlink = HyperlinkUtil.getIgnoreHyperlink(DocxReportConfiguration.PROPERTY_IGNORE_HYPERLINK, link);
		if (ignoreHyperlink == null)
		{
			ignoreHyperlink = getCurrentItemConfiguration().isIgnoreHyperlink();
		}

		if (!ignoreHyperlink)
		{
			JRHyperlinkProducer customHandler = getHyperlinkProducer(link);
			if (customHandler == null)
			{
				switch(link.getHyperlinkTypeValue())
				{
					case REFERENCE :
					{
						if (link.getHyperlinkReference() != null)
						{
							href = link.getHyperlinkReference();
						}
						break;
					}
					case LOCAL_ANCHOR :
					{
						if (link.getHyperlinkAnchor() != null)
						{
							href = link.getHyperlinkAnchor().replaceAll("\\W", "");
						}
						break;
					}
					case LOCAL_PAGE :
					{
						if (link.getHyperlinkPage() != null)
						{
							href = JR_PAGE_ANCHOR_PREFIX + reportIndex + "_" + link.getHyperlinkPage().toString();
						}
						break;
					}
					case REMOTE_ANCHOR :
					{
						if (
							link.getHyperlinkReference() != null &&
							link.getHyperlinkAnchor() != null
							)
						{
							href = link.getHyperlinkReference() + "#" + link.getHyperlinkAnchor();
						}
						break;
					}
					case REMOTE_PAGE :
					{
						if (
							link.getHyperlinkReference() != null &&
							link.getHyperlinkPage() != null
							)
						{
							href = link.getHyperlinkReference() + "#" + JR_PAGE_ANCHOR_PREFIX + reportIndex + "_" + link.getHyperlinkPage().toString();
						}
						break;
					}
					case NONE :
					default :
					{
						break;
					}
				}
			}
			else
			{
				href = customHandler.getHyperlink(link);
			}
		}

		return href;
	}


	protected void endHyperlink(boolean isText)
	{
//		docHelper.write("</w:hyperlink>\n");
		docHelper.write("<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>\n");
	}

	protected void insertBookmark(String bookmark, BaseHelper helper)
	{
		helper.write("<w:bookmarkStart w:id=\"" + bookmarkIndex);
		helper.write("\" w:name=\"" + (bookmark == null ? null : bookmark.replaceAll("\\W", "")));
		helper.write("\"/><w:bookmarkEnd w:id=\"" + bookmarkIndex++);
		helper.write("\"/>");
	}
	
	@Override
	protected void ensureInput()
	{
		super.ensureInput();
	}

	@Override
	public String getExporterKey()
	{
		return DOCX_EXPORTER_KEY;
	}

	@Override
	public String getExporterPropertiesPrefix()
	{
		return DOCX_EXPORTER_PROPERTIES_PREFIX;
	}
	
}

