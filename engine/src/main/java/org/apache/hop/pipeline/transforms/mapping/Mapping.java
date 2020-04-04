/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2019 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.mapping;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hop.core.Const;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.Result;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogTableField;
import org.apache.hop.core.logging.PipelineLogTable;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelineMeta.PipelineType;
import org.apache.hop.pipeline.SingleThreadedPipelineExecutor;
import org.apache.hop.pipeline.TransformWithMappingMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.IRowListener;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaDataCombi;
import org.apache.hop.pipeline.transforms.PipelineTransformUtil;
import org.apache.hop.pipeline.transforms.mappinginput.MappingInput;
import org.apache.hop.pipeline.transforms.mappingoutput.MappingOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Execute a mapping: a re-usuable pipeline
 *
 * @author Matt
 * @since 22-nov-2005
 */
public class Mapping extends BaseTransform<MappingMeta, MappingData> implements ITransform<MappingMeta, MappingData> {

  private static Class<?> PKG = MappingMeta.class; // for i18n purposes, needed by Translator!!

  public Mapping( TransformMeta transformMeta, MappingMeta meta, MappingData data, int copyNr, PipelineMeta pipelineMeta, Pipeline pipeline ) {
    super( transformMeta, meta, data, copyNr, pipelineMeta, pipeline );
  }

  /**
   * Process a single row. In our case, we send one row of data to a piece of pipeline. In the pipeline, we
   * look up the MappingInput transform to send our rows to it. As a consequence, for the time being, there can only be one
   * MappingInput and one MappingOutput transform in the Mapping.
   */
  @Override
  public boolean processRow() throws HopException {
    try {

      MappingInput[] mappingInputs = data.getMappingPipeline().findMappingInput();
      MappingOutput[] mappingOutputs = data.getMappingPipeline().findMappingOutput();

      data.wasStarted = true;
      switch ( data.mappingPipelineMeta.getPipelineType() ) {
        case Normal:

          // Before we start, let's see if there are loose ends to tie up...
          //
          List<IRowSet> inputRowSets = getInputRowSets();
          if ( !inputRowSets.isEmpty() ) {
            for ( IRowSet rowSet : inputRowSets ) {
              // Pass this rowset down to a mapping input transform in the
              // sub-pipeline...
              //
              if ( mappingInputs.length == 1 ) {
                // Simple case: only one input mapping. Move the IRowSet over
                //
                mappingInputs[ 0 ].addRowSetToInputRowSets( rowSet );
              } else {
                // Difficult to see what's going on here.
                // TODO: figure out where this IRowSet needs to go and where it
                // comes from.
                //
                throw new HopException(
                  "Unsupported situation detected where more than one Mapping Input transform needs to be handled.  "
                    + "To solve it, insert a dummy transform before the mapping transform." );
              }
            }
            clearInputRowSets();
          }


          // Do the same thing for output row sets
          //
          List<IRowSet> outputRowSets = getOutputRowSets();
          if ( !outputRowSets.isEmpty() ) {
            for ( IRowSet rowSet : outputRowSets ) {
              // Pass this rowset down to a mapping input transform in the
              // sub-pipeline...
              //
              if ( mappingOutputs.length == 1 ) {
                // Simple case: only one output mapping. Move the IRowSet over
                //
                mappingOutputs[ 0 ].addRowSetToOutputRowSets( rowSet );
              } else {
                // Difficult to see what's going on here.
                // TODO: figure out where this IRowSet needs to go and where it
                // comes from.
                //
                throw new HopException(
                  "Unsupported situation detected where more than one Mapping Output transform needs to be handled.  "
                    + "To solve it, insert a dummy transform after the mapping transform." );
              }
            }
            clearOutputRowSets();
          }

          // Start the mapping/sub- pipeline threads
          //
          data.getMappingPipeline().startThreads();

          // The pipeline still runs in the background and might have some
          // more work to do.
          // Since everything is running in the MappingThreads we don't have to do
          // anything else here but wait...
          //
          if ( getPipelineMeta().getPipelineType() == PipelineType.Normal ) {
            data.getMappingPipeline().waitUntilFinished();

            // Set some statistics from the mapping...
            // This will show up in HopGui, etc.
            //
            Result result = data.getMappingPipeline().getResult();
            setErrors( result.getNrErrors() );
            setLinesRead( result.getNrLinesRead() );
            setLinesWritten( result.getNrLinesWritten() );
            setLinesInput( result.getNrLinesInput() );
            setLinesOutput( result.getNrLinesOutput() );
            setLinesUpdated( result.getNrLinesUpdated() );
            setLinesRejected( result.getNrLinesRejected() );
          }
          return false;

        case SingleThreaded:

          if ( mappingInputs.length > 1 || mappingOutputs.length > 1 ) {
            throw new HopException(
              "Multiple input or output transforms are not supported for a single threaded mapping." );
          }

          // Object[] row = getRow();
          // IRowMeta rowMeta = getInputRowMeta();

          // for (int count=0;count<(data.mappingPipelineMeta.getSizeRowset()/2) && row!=null;count++) {
          // // Pass each row over to the mapping input transform, fill the buffer...
          //
          // mappingInputs[0].getInputRowSets().get(0).putRow(rowMeta, row);
          //
          // row = getRow();
          // }

          if ( ( log != null ) && log.isDebug() ) {
            List<IRowSet> mappingInputRowSets = mappingInputs[ 0 ].getInputRowSets();
            log.logDebug( "# of input buffers: " + mappingInputRowSets.size() );
            if ( mappingInputRowSets.size() > 0 ) {
              log.logDebug( "Input buffer 0 size: " + mappingInputRowSets.get( 0 ).size() );
            }
          }

          // Now execute one batch...Basic logging
          //
          boolean result = data.singleThreadedPipelineExecutor.oneIteration();
          if ( !result ) {
            data.singleThreadedPipelineExecutor.dispose();
            setOutputDone();
            return false;
          }
          return true;

        default:
          throw new HopException( "Pipeline type '"
            + data.mappingPipelineMeta.getPipelineType().getDescription()
            + "' is an unsupported pipeline type for a mapping" );
      }
    } catch ( Throwable t ) {
      // Some unexpected situation occurred.
      // Better to stop the mapping pipeline.
      //
      if ( data.getMappingPipeline() != null ) {
        data.getMappingPipeline().stopAll();
      }

      // Forward the exception...
      //
      throw new HopException( t );
    }
  }


  public void prepareMappingExecution() throws HopException {
    initPipelineFromMeta();
    MappingData mappingData = data;
    // We launch the pipeline in the processRow when the first row is
    // received.
    // This will allow the correct variables to be passed.
    // Otherwise the parent is the init() thread which will be gone once the
    // init is done.
    //
    try {
      mappingData.getMappingPipeline().prepareExecution();
    } catch ( HopException e ) {
      throw new HopException( BaseMessages.getString( PKG, "Mapping.Exception.UnableToPrepareExecutionOfMapping" ),
        e );
    }

    // Extra optional work to do for alternative execution engines...
    //
    switch ( mappingData.mappingPipelineMeta.getPipelineType() ) {
      case Normal:
        break;

      case SingleThreaded:
        mappingData.singleThreadedPipelineExecutor = new SingleThreadedPipelineExecutor( mappingData.getMappingPipeline() );
        if ( !mappingData.singleThreadedPipelineExecutor.init() ) {
          throw new HopException( BaseMessages.getString( PKG,
            "Mapping.Exception.UnableToInitSingleThreadedPipeline" ) );
        }
        break;
      default:
        break;
    }

    // If there is no read/write logging transform set, we can insert the data from
    // the first mapping input/output transform...
    //
    MappingInput[] mappingInputs = mappingData.getMappingPipeline().findMappingInput();
    LogTableField readField = mappingData.mappingPipelineMeta.getPipelineLogTable().findField( PipelineLogTable.ID.LINES_READ );
    if ( readField.getSubject() == null && mappingInputs != null && mappingInputs.length >= 1 ) {
      readField.setSubject( mappingInputs[ 0 ].getTransformMeta() );
    }
    MappingOutput[] mappingOutputs = mappingData.getMappingPipeline().findMappingOutput();
    LogTableField writeField = mappingData.mappingPipelineMeta.getPipelineLogTable().findField( PipelineLogTable.ID.LINES_WRITTEN );
    if ( writeField.getSubject() == null && mappingOutputs != null && mappingOutputs.length >= 1 ) {
      writeField.setSubject( mappingOutputs[ 0 ].getTransformMeta() );
    }

    // Before we add rowsets and all, we should note that the mapping transform did
    // not receive ANY input and output rowsets.
    // This is an exception to the general rule, built into
    // Pipeline.prepareExecution()
    //
    // A Mapping Input transform is supposed to read directly from the previous
    // transforms.
    // A Mapping Output transform is supposed to write directly to the next transforms.

    // OK, check the input mapping definitions and look up the transforms to read
    // from.
    //
    ITransform[] sourceTransforms;
    for ( MappingIODefinition inputDefinition : meta.getInputMappings() ) {
      // If we have a single transform to read from, we use this
      //
      if ( !Utils.isEmpty( inputDefinition.getInputTransformName() ) ) {
        ITransform sourceTransform = getPipeline().findRunThread( inputDefinition.getInputTransformName() );
        if ( sourceTransform == null ) {
          throw new HopException( BaseMessages.getString( PKG, "MappingDialog.Exception.TransformNameNotFound",
            inputDefinition.getInputTransformName() ) );
        }
        sourceTransforms = new ITransform[] { sourceTransform, };
      } else {
        // We have no defined source transform.
        // That means that we're reading from all input transforms that this mapping
        // transform has.
        //
        List<TransformMeta> prevTransforms = getPipelineMeta().findPreviousTransforms( getTransformMeta() );

        // TODO: Handle remote transforms from: getTransformMeta().getRemoteInputTransforms()
        //

        // Let's read data from all the previous transforms we find...
        // The origin is the previous transform
        // The target is the Mapping Input transform.
        //
        sourceTransforms = new ITransform[ prevTransforms.size() ];
        for ( int s = 0; s < sourceTransforms.length; s++ ) {
          sourceTransforms[ s ] = getPipeline().findRunThread( prevTransforms.get( s ).getName() );
        }
      }

      // What transform are we writing to?
      MappingInput mappingInputTarget = null;
      MappingInput[] mappingInputTransforms = mappingData.getMappingPipeline().findMappingInput();
      if ( Utils.isEmpty( inputDefinition.getOutputTransformName() ) ) {
        // No target was specifically specified.
        // That means we only expect one "mapping input" transform in the mapping...

        if ( mappingInputTransforms.length == 0 ) {
          throw new HopException( BaseMessages
            .getString( PKG, "MappingDialog.Exception.OneMappingInputTransformRequired" ) );
        }
        if ( mappingInputTransforms.length > 1 ) {
          throw new HopException( BaseMessages.getString( PKG,
            "MappingDialog.Exception.OnlyOneMappingInputTransformAllowed", "" + mappingInputTransforms.length ) );
        }

        mappingInputTarget = mappingInputTransforms[ 0 ];
      } else {
        // A target transform was specified. See if we can find it...
        for ( int s = 0; s < mappingInputTransforms.length && mappingInputTarget == null; s++ ) {
          if ( mappingInputTransforms[ s ].getTransformName().equals( inputDefinition.getOutputTransformName() ) ) {
            mappingInputTarget = mappingInputTransforms[ s ];
          }
        }
        // If we still didn't find it it's a drag.
        if ( mappingInputTarget == null ) {
          throw new HopException( BaseMessages.getString( PKG, "MappingDialog.Exception.TransformNameNotFound",
            inputDefinition.getOutputTransformName() ) );
        }
      }

      // Before we pass the field renames to the mapping input transform, let's add
      // functionality to rename it back on ALL
      // mapping output transforms.
      // To do this, we need a list of values that changed so we can revert that
      // in the metadata before the rows come back.
      //
      if ( inputDefinition.isRenamingOnOutput() ) {
        addInputRenames( data.inputRenameList, inputDefinition.getValueRenames() );
      }

      mappingInputTarget.setConnectorTransforms( sourceTransforms, inputDefinition.getValueRenames(), getTransformName() );
    }

    // Now we have a List of connector threads.
    // If we start all these we'll be starting to pump data into the mapping
    // If we don't have any threads to start, nothings going in there...
    // However, before we send anything over, let's first explain to the mapping
    // output transforms where the data needs to go...
    //
    for ( MappingIODefinition outputDefinition : meta.getOutputMappings() ) {
      // OK, what is the source (input) transform in the mapping: it's the mapping
      // output transform...
      // What transform are we reading from here?
      //
      MappingOutput mappingOutputSource =
        (MappingOutput) mappingData.getMappingPipeline().findRunThread( outputDefinition.getInputTransformName() );
      if ( mappingOutputSource == null ) {
        // No source transform was specified: we're reading from a single Mapping
        // Output transform.
        // We should verify this if this is really the case...
        //
        MappingOutput[] mappingOutputTransforms = mappingData.getMappingPipeline().findMappingOutput();

        if ( mappingOutputTransforms.length == 0 ) {
          throw new HopException( BaseMessages.getString( PKG,
            "MappingDialog.Exception.OneMappingOutputTransformRequired" ) );
        }
        if ( mappingOutputTransforms.length > 1 ) {
          throw new HopException( BaseMessages.getString( PKG,
            "MappingDialog.Exception.OnlyOneMappingOutputTransformAllowed", "" + mappingOutputTransforms.length ) );
        }

        mappingOutputSource = mappingOutputTransforms[ 0 ];
      }

      // To what transforms in this pipeline are we writing to?
      //
      ITransform[] targetTransforms = pickupTargetTransformsFor( outputDefinition );

      // Now tell the mapping output transform where to look...
      // Also explain the mapping output transforms how to rename the values back...
      //
      mappingOutputSource
        .setConnectorTransforms( targetTransforms, data.inputRenameList, outputDefinition.getValueRenames() );

      // Is this mapping copying or distributing?
      // Make sure the mapping output transform mimics this behavior:
      //
      mappingOutputSource.setDistributed( isDistributed() );
    }

    // Finally, add the mapping pipeline to the active sub-pipelines
    // map in the parent pipeline
    //
    getPipeline().addActiveSubPipeline( getTransformName(), data.getMappingPipeline() );
  }

  @VisibleForTesting ITransform[] pickupTargetTransformsFor( MappingIODefinition outputDefinition )
    throws HopException {
    List<ITransform> result;
    if ( !Utils.isEmpty( outputDefinition.getOutputTransformName() ) ) {
      // If we have a target transform specification for the output of the mapping,
      // we need to send it over there...
      //
      result = getPipeline().findTransformInterfaces( outputDefinition.getOutputTransformName() );
      if ( Utils.isEmpty( result ) ) {
        throw new HopException( BaseMessages.getString( PKG, "MappingDialog.Exception.TransformNameNotFound",
          outputDefinition.getOutputTransformName() ) );
      }
    } else {
      // No target transform is specified.
      // See if we can find the next transforms in the pipeline..
      //
      List<TransformMeta> nextTransforms = getPipelineMeta().findNextTransforms( getTransformMeta() );

      // Let's send the data to all the next transforms we find...
      // The origin is the mapping output transform
      // The target is all the next transforms after this mapping transform.
      //
      result = new ArrayList<>();
      for ( TransformMeta nextTransform : nextTransforms ) {
        // need to take into the account different copies of the transform
        List<ITransform> copies = getPipeline().findTransformInterfaces( nextTransform.getName() );
        if ( copies != null ) {
          result.addAll( copies );
        }
      }
    }
    return result.toArray( new ITransform[ result.size() ] );
  }

  void initPipelineFromMeta() throws HopException {
    // Create the pipeline from meta-data...
    //
    data.setMappingPipeline( new Pipeline( data.mappingPipelineMeta, this ) );

    if ( data.mappingPipelineMeta.getPipelineType() != PipelineType.Normal ) {
      data.getMappingPipeline().getPipelineMeta().setUsingThreadPriorityManagment( false );
    }

    // Leave a path up so that we can set variables in sub-pipelines...
    //
    data.getMappingPipeline().setParentPipeline( getPipeline() );

    // Pass down the safe mode flag to the mapping...
    //
    data.getMappingPipeline().setSafeModeEnabled( getPipeline().isSafeModeEnabled() );

    // Pass down the metrics gathering flag:
    //
    data.getMappingPipeline().setGatheringMetrics( getPipeline().isGatheringMetrics() );

    // Also set the name of this transform in the mapping pipeline for logging
    // purposes
    //
    data.getMappingPipeline().setMappingTransformName( getTransformName() );

    initServletConfig();

    // Set the parameters values in the mapping.
    //

    MappingParameters mappingParameters = meta.getMappingParameters();
    if ( mappingParameters != null ) {
      TransformWithMappingMeta
        .activateParams( data.mappingPipeline, data.mappingPipeline, this, data.mappingPipelineMeta.listParameters(),
          mappingParameters.getVariable(), mappingParameters.getInputField(), meta.getMappingParameters().isInheritingAllVariables() );
    }

  }

  void initServletConfig() {
    PipelineTransformUtil.initServletConfig( getPipeline(), data.getMappingPipeline() );
  }

  public static void addInputRenames( List<MappingValueRename> renameList, List<MappingValueRename> addRenameList ) {
    for ( MappingValueRename rename : addRenameList ) {
      if ( renameList.indexOf( rename ) < 0 ) {
        renameList.add( rename );
      }
    }
  }

  @Override
  public boolean init() {
    if ( !super.init() ) {
      return false;
    }
    // First we need to load the mapping (pipeline)
    try {
      // Pass the MetaStore down to the metadata object...
      //
      data.mappingPipelineMeta = MappingMeta.loadMappingMeta( meta, meta.getMetaStore(), this, meta.getMappingParameters().isInheritingAllVariables() );
      if ( data.mappingPipelineMeta == null ) {
        // Do we have a mapping at all?
        logError( "No valid mapping was specified!" );
        return false;
      }

      // OK, now prepare the execution of the mapping.
      // This includes the allocation of IRowSet buffers, the creation of the
      // sub- pipeline threads, etc.
      //
      prepareMappingExecution();

      lookupStatusTransformNumbers();
      // That's all for now...
      return true;
    } catch ( Exception e ) {
      logError( "Unable to load the mapping pipeline because of an error : " + e.toString() );
      logError( Const.getStackTracker( e ) );
      return false;
    }
  }

  @Override
  public void dispose() {
    // Close the running pipeline
    if ( data.wasStarted ) {
      if ( !data.mappingPipeline.isFinished() ) {
        // Wait until the child pipeline has finished.
        data.getMappingPipeline().waitUntilFinished();
      }
      // Remove it from the list of active sub-pipelines...
      //
      getPipeline().removeActiveSubPipeline( getTransformName() );

      // See if there was an error in the sub-pipeline, in that case, flag error etc.
      if ( data.getMappingPipeline().getErrors() > 0 ) {
        logError( BaseMessages.getString( PKG, "Mapping.Log.ErrorOccurredInSubPipeline" ) );
        setErrors( 1 );
      }
    }
    super.dispose();
  }

  @Override
  public void stopRunning()
    throws HopException {
    if ( data.getMappingPipeline() != null ) {
      data.getMappingPipeline().stopAll();
    }
  }

  public void stopAll() {
    // Stop the mapping transform.
    if ( data.getMappingPipeline() != null ) {
      data.getMappingPipeline().stopAll();
    }

    // Also stop this transform
    super.stopAll();
  }

  private void lookupStatusTransformNumbers() {
    if ( data.getMappingPipeline() != null ) {
      List<TransformMetaDataCombi<ITransform, ITransformMeta, ITransformData>> transforms = data.getMappingPipeline().getTransforms();
      for ( int i = 0; i < transforms.size(); i++ ) {
        TransformMetaDataCombi sid = transforms.get( i );
        BaseTransform rt = (BaseTransform) sid.transform;
        if ( rt.getTransformName().equals( data.mappingPipelineMeta.getPipelineLogTable().getTransformNameRead() ) ) {
          data.linesReadTransformNr = i;
        }
        if ( rt.getTransformName().equals( data.mappingPipelineMeta.getPipelineLogTable().getTransformNameInput() ) ) {
          data.linesInputTransformNr = i;
        }
        if ( rt.getTransformName().equals( data.mappingPipelineMeta.getPipelineLogTable().getTransformNameWritten() ) ) {
          data.linesWrittenTransformNr = i;
        }
        if ( rt.getTransformName().equals( data.mappingPipelineMeta.getPipelineLogTable().getTransformNameOutput() ) ) {
          data.linesOutputTransformNr = i;
        }
        if ( rt.getTransformName().equals( data.mappingPipelineMeta.getPipelineLogTable().getTransformNameUpdated() ) ) {
          data.linesUpdatedTransformNr = i;
        }
        if ( rt.getTransformName().equals( data.mappingPipelineMeta.getPipelineLogTable().getTransformNameRejected() ) ) {
          data.linesRejectedTransformNr = i;
        }
      }
    }
  }

  @Override
  public long getLinesInput() {
    if ( data.linesInputTransformNr != -1 ) {
      return data.getMappingPipeline().getTransforms().get( data.linesInputTransformNr ).transform.getLinesInput();
    } else {
      return 0;
    }
  }

  @Override
  public long getLinesOutput() {
    if ( data.linesOutputTransformNr != -1 ) {
      return data.getMappingPipeline().getTransforms().get( data.linesOutputTransformNr ).transform.getLinesOutput();
    } else {
      return 0;
    }
  }

  @Override
  public long getLinesRead() {
    if ( data.linesReadTransformNr != -1 ) {
      return data.getMappingPipeline().getTransforms().get( data.linesReadTransformNr ).transform.getLinesRead();
    } else {
      return 0;
    }
  }

  @Override
  public long getLinesRejected() {
    if ( data.linesRejectedTransformNr != -1 ) {
      return data.getMappingPipeline().getTransforms().get( data.linesRejectedTransformNr ).transform.getLinesRejected();
    } else {
      return 0;
    }
  }

  @Override
  public long getLinesUpdated() {
    if ( data.linesUpdatedTransformNr != -1 ) {
      return data.getMappingPipeline().getTransforms().get( data.linesUpdatedTransformNr ).transform.getLinesUpdated();
    } else {
      return 0;
    }
  }

  @Override
  public long getLinesWritten() {
    if ( data.linesWrittenTransformNr != -1 ) {
      return data.getMappingPipeline().getTransforms().get( data.linesWrittenTransformNr ).transform.getLinesWritten();
    } else {
      return 0;
    }
  }

  @Override
  public int rowsetInputSize() {
    int size = 0;
    for ( MappingInput input : data.getMappingPipeline().findMappingInput() ) {
      for ( IRowSet rowSet : input.getInputRowSets() ) {
        size += rowSet.size();
      }
    }
    return size;
  }

  @Override
  public int rowsetOutputSize() {
    int size = 0;
    for ( MappingOutput output : data.getMappingPipeline().findMappingOutput() ) {
      for ( IRowSet rowSet : output.getOutputRowSets() ) {
        size += rowSet.size();
      }
    }
    return size;
  }

  public Pipeline getMappingPipeline() {
    return data.getMappingPipeline();
  }

  /**
   * For preview of the main data path, make sure we pass the row listener down to the Mapping Output transform...
   */
  public void addRowListener( IRowListener rowListener ) {
    MappingOutput[] mappingOutputs = data.getMappingPipeline().findMappingOutput();
    if ( mappingOutputs == null || mappingOutputs.length == 0 ) {
      return; // Nothing to do here...
    }

    // Simple case: one output mapping transform : add the row listener over there
    //
    /*
     * if (mappingOutputs.length==1) { mappingOutputs[0].addRowListener(rowListener); } else { // Find the main data
     * path... //
     *
     *
     * }
     */

    // Add the row listener to all the outputs in the mapping...
    //
    for ( MappingOutput mappingOutput : mappingOutputs ) {
      mappingOutput.addRowListener( rowListener );
    }
  }
}