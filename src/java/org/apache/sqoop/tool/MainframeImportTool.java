/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sqoop.tool;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.util.ToolRunner;
import org.apache.sqoop.manager.MainframeManager;
import org.apache.sqoop.mapreduce.mainframe.MainframeConfiguration;

import org.apache.sqoop.SqoopOptions;
import org.apache.sqoop.SqoopOptions.InvalidOptionsException;
import org.apache.sqoop.cli.RelatedOptions;
import org.apache.sqoop.cli.ToolOptions;

/**
 * Tool that performs mainframe dataset imports to HDFS.
 */
public class MainframeImportTool extends ImportTool {
  private static final Log LOG
      = LogFactory.getLog(MainframeImportTool.class.getName());
  public static final String DS_ARG = "dataset";
  public static final String DS_TYPE_ARG = "datasettype";
  public static final String DS_TAPE_ARG = "tape";
  public static final String BUFFERSIZE_ARG = "buffersize";
  public static final String FTP_COMMANDS = "ftp-commands";

  public MainframeImportTool() {
    super("import-mainframe", false);
  }

  @Override
  @SuppressWarnings("static-access")
  protected RelatedOptions getImportOptions() {
    // Imports
    RelatedOptions importOpts
        = new RelatedOptions("Import mainframe control arguments");
    importOpts.addOption(OptionBuilder.withArgName("Dataset name")
        .hasArg().withDescription("Datasets to import")
        .withLongOpt(DS_ARG)
        .create());
    importOpts.addOption(OptionBuilder
        .withDescription("Imports data in delete mode")
        .withLongOpt(DELETE_ARG)
        .create());
    importOpts.addOption(OptionBuilder.withArgName("dir")
        .hasArg().withDescription("HDFS plain file destination")
        .withLongOpt(TARGET_DIR_ARG)
        .create());
    importOpts.addOption(OptionBuilder.withArgName("Dataset type")
            .hasArg().withDescription("Dataset type (p=partitioned data set|s=sequential data set|g=GDG)")
            .withLongOpt(DS_TYPE_ARG)
            .create());
    importOpts.addOption(OptionBuilder.withArgName("Dataset is on tape")
    		.hasArg().withDescription("Dataset is on tape (true|false)")
    		.withLongOpt(DS_TAPE_ARG)
    		.create());

    addValidationOpts(importOpts);

    importOpts.addOption(OptionBuilder.withArgName("dir")
        .hasArg().withDescription("HDFS parent for file destination")
        .withLongOpt(WAREHOUSE_DIR_ARG)
        .create());
    importOpts.addOption(OptionBuilder
        .withDescription("Imports data as plain text (default)")
        .withLongOpt(FMT_TEXTFILE_ARG)
        .create());
    importOpts.addOption(OptionBuilder
      .withDescription("Imports data as binary")
      .withLongOpt(FMT_BINARYFILE_ARG)
      .create());
    importOpts.addOption(OptionBuilder
      .hasArg().withDescription("Sets buffer size for binary import in bytes (default=32kB)")
      .withLongOpt(BUFFERSIZE_ARG)
      .create());
    importOpts.addOption(OptionBuilder.withArgName("Comma separated FTP commands issued before FTP transfer")
      .hasArg().withDescription("Additional FTP commands issued before transfer")
      .withLongOpt(FTP_COMMANDS)
      .create());
    importOpts.addOption(OptionBuilder.withArgName("n")
        .hasArg().withDescription("Use 'n' map tasks to import in parallel")
        .withLongOpt(NUM_MAPPERS_ARG)
        .create(NUM_MAPPERS_SHORT_ARG));
    importOpts.addOption(OptionBuilder.withArgName("name")
        .hasArg().withDescription("Set name for generated mapreduce job")
        .withLongOpt(MAPREDUCE_JOB_NAME)
        .create());
    importOpts.addOption(OptionBuilder
        .withDescription("Enable compression")
        .withLongOpt(COMPRESS_ARG)
        .create(COMPRESS_SHORT_ARG));
    importOpts.addOption(OptionBuilder.withArgName("codec")
        .hasArg()
        .withDescription("Compression codec to use for import")
        .withLongOpt(COMPRESSION_CODEC_ARG)
        .create());

    return importOpts;
  }

  @Override
  public void configureOptions(ToolOptions toolOptions) {
    toolOptions.addUniqueOptions(getCommonOptions());
    toolOptions.addUniqueOptions(getImportOptions());
    toolOptions.addUniqueOptions(getOutputFormatOptions());
    toolOptions.addUniqueOptions(getInputFormatOptions());
    toolOptions.addUniqueOptions(getHiveOptions(true));
    toolOptions.addUniqueOptions(getHBaseOptions());
    toolOptions.addUniqueOptions(getHCatalogOptions());
    toolOptions.addUniqueOptions(getHCatImportOnlyOptions());
    toolOptions.addUniqueOptions(getAccumuloOptions());

    // get common codegen opts.
    RelatedOptions codeGenOpts = getCodeGenOpts(false);

    // add import-specific codegen opts:
    codeGenOpts.addOption(OptionBuilder.withArgName("file")
        .hasArg()
        .withDescription("Disable code generation; use specified jar")
        .withLongOpt(JAR_FILE_NAME_ARG)
        .create());

    toolOptions.addUniqueOptions(codeGenOpts);
  }

  @Override
  public void printHelp(ToolOptions toolOptions) {
    System.out.println("usage: sqoop " + getToolName()
        + " [GENERIC-ARGS] [TOOL-ARGS]\n");

    toolOptions.printHelp();

    System.out.println("\nGeneric Hadoop command-line arguments:");
    System.out.println("(must preceed any tool-specific arguments)");
    ToolRunner.printGenericCommandUsage(System.out);
    System.out.println(
        "\nAt minimum, you must specify --connect and --" + DS_ARG);
  }

  @Override
  public void applyOptions(CommandLine in, SqoopOptions out)
      throws InvalidOptionsException {
    super.applyOptions(in, out);

    if (!in.hasOption(CONN_MANAGER_CLASS_NAME)) {
       // set default ConnManager
      out.setConnManagerClassName(MainframeManager.class.getName());
    }
    if (in.hasOption(DS_ARG)) {
      out.setMainframeInputDatasetName(in.getOptionValue(DS_ARG));
    }

    if (in.hasOption(DS_TYPE_ARG)) {
      out.setMainframeInputDatasetType(in.getOptionValue(DS_TYPE_ARG));
    } else {
    	// set default data set type to partitioned
    	out.setMainframeInputDatasetType(MainframeConfiguration.MAINFRAME_INPUT_DATASET_TYPE_PARTITIONED);
    }

    if (in.hasOption(DS_TAPE_ARG)) {
    	out.setMainframeInputDatasetTape(in.getOptionValue(DS_TAPE_ARG));
    } else {
    	// set default tape value to false
    	out.setMainframeInputDatasetTape("false");
    }
    if (in.hasOption(FMT_BINARYFILE_ARG)) {
      out.setMainframeFtpTransferMode(MainframeConfiguration.MAINFRAME_FTP_TRANSFER_MODE_BINARY);
      out.setFileLayout(SqoopOptions.FileLayout.BinaryFile);
    } else {
      // set default transfer mode to ascii
      out.setMainframeFtpTransferMode(MainframeConfiguration.MAINFRAME_FTP_TRANSFER_MODE_ASCII);
      out.setFileLayout(SqoopOptions.FileLayout.TextFile);
    }

    if (in.hasOption(BUFFERSIZE_ARG)) {
      // if we specify --buffersize set the buffer size
      int bufSize = Integer.valueOf(in.getOptionValue(BUFFERSIZE_ARG));
      if (bufSize > 0) {
        out.setBufferSize(bufSize);
      }
      else {
        out.setBufferSize(MainframeConfiguration.MAINFRAME_FTP_TRANSFER_BINARY_DEFAULT_BUFFER_SIZE);
      }
    } else {
      // set the default buffer size to 32kB
      out.setBufferSize(MainframeConfiguration.MAINFRAME_FTP_TRANSFER_BINARY_DEFAULT_BUFFER_SIZE);
    }
    if (in.hasOption(FTP_COMMANDS)) {
      out.setFtpCommands(in.getOptionValue(FTP_COMMANDS));
    }
  }

  @Override
  protected void validateImportOptions(SqoopOptions options)
      throws InvalidOptionsException {
    if (options.getMainframeInputDatasetName() == null) {
      throw new InvalidOptionsException(
          "--" + DS_ARG + " is required for mainframe import. " + HELP_STR);
    }
    String dsType = options.getMainframeInputDatasetType();
    LOG.info("Dataset type: "+dsType);
    if (!dsType.equals(MainframeConfiguration.MAINFRAME_INPUT_DATASET_TYPE_PARTITIONED)
    		&& !dsType.equals(MainframeConfiguration.MAINFRAME_INPUT_DATASET_TYPE_SEQUENTIAL)
    		&& !dsType.equals(MainframeConfiguration.MAINFRAME_INPUT_DATASET_TYPE_GDG)) {
      throw new InvalidOptionsException(
    		  "--" + DS_TYPE_ARG + " specified is invalid. " + HELP_STR);
    }
    Boolean dsTape = options.getMainframeInputDatasetTape();
	if (dsTape == null && dsTape != true && dsTape != false) {
		throw new InvalidOptionsException(
				"--" + DS_TAPE_ARG + " specified is invalid. " + HELP_STR);
	}
    /* only allow FileLayout.BinaryFile to be selected for mainframe import */
    if (SqoopOptions.FileLayout.BinaryFile.equals(options.getFileLayout()) && StringUtils.isEmpty(options.getMainframeInputDatasetName())) {
      throw new InvalidOptionsException("--as-binaryfile should only be used with import-mainframe module.");
    }

    // only allow buffer size to be set different to default when binary file is selected
    // in any case, if --as-binaryfile isn't selected, --buffersize parameter is harmless
    if (!SqoopOptions.FileLayout.BinaryFile.equals(options.getFileLayout()) && !MainframeConfiguration.MAINFRAME_FTP_TRANSFER_BINARY_DEFAULT_BUFFER_SIZE.equals(options.getBufferSize())) {
      throw new InvalidOptionsException("--buffersize should only be used with --as-binaryfile parameter.");
    }

    super.validateImportOptions(options);
  }
}
