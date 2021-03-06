/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
 * http://www.project-hop.org
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

package org.apache.hop.pipeline.transforms.ifnull;

import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

import java.util.HashMap;

public class IfNullData extends BaseTransformData implements ITransformData {

  public IRowMeta outputRowMeta;
  public IRowMeta convertRowMeta;

  public int[] fieldnrs;
  public int fieldnr;
  public String realReplaceByValue;
  public String realconversionMask;
  public boolean realSetEmptyString;

  public HashMap<String, Integer> ListTypes;
  public String[] defaultValues;
  public String[] defaultMasks;
  public boolean[] setEmptyString;

  public IfNullData() {
    super();
    ListTypes = new HashMap<String, Integer>();
  }

}
