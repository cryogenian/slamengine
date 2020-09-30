/*
 * Copyright 2020 Precog Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.impl.parsing

import slamdata.Predef.{None, Option, Some, String, Throwable}

import java.util.{zip => j}

import quasar.api.resource.ResourcePath
import quasar.connector.{DataFormat, ResourceError, CompressionScheme}, DataFormat.JsonVariant

import tectonic.{IncompleteParseException, ParseException}

object ResourceErrorRecognizer {

  object ZipException {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case ex: j.ZipException => Some(ex)
      case _ => None
    }
  }

  def apply(path: ResourcePath, tpe: DataFormat, cause: Throwable): Option[ResourceError] =
    Some(cause) collect {
      case ParseException(msg, _, _, _) =>
        ResourceError.malformedResource(path, typeSummary(tpe), Some(msg), Some(cause))

      case IncompleteParseException(msg) =>
        ResourceError.malformedResource(path, typeSummary(tpe), Some(msg), Some(cause))

      case ZipException(_) =>
        ResourceError.malformedResource(path, typeSummary(tpe), Some(cause.getMessage), Some(cause))
    }

  val typeSummary: DataFormat => String = {
    case DataFormat.Compressed(CompressionScheme.Gzip, pt) =>
      "gzipped " + typeSummary(pt: DataFormat)
    case DataFormat.Compressed(CompressionScheme.Zip, pt) =>
      "zipped " + typeSummary(pt: DataFormat)
    case v: DataFormat.SeparatedValues =>
      "separated values"
    case DataFormat.Json(vnt, isPrecise) =>
      variantString(vnt) + (if (isPrecise) " (precise)" else "")
  }

  private val variantString: DataFormat.JsonVariant => String = {
    case JsonVariant.ArrayWrapped => "json"
    case JsonVariant.LineDelimited => "ldjson"
  }
}
