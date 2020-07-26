/*
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.file.footer.ChunkGroupFooter;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetadata;
import org.apache.iotdb.tsfile.file.metadata.MetadataIndexEntry;
import org.apache.iotdb.tsfile.file.metadata.TsFileMetadata;
import org.apache.iotdb.tsfile.fileSystem.FSFactoryProducer;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.Chunk;
import org.apache.iotdb.tsfile.utils.BloomFilter;

public class TsFileSketchTool {

  public static void main(String[] args) throws IOException {
    String filename = "test.tsfile";
    String outFile = "TsFile_sketch_view.txt";
    if (args.length == 1) {
      filename = args[0];
    } else if (args.length == 2) {
      filename = args[0];
      outFile = args[1];
    }
    System.out.println("TsFile path:" + filename);
    System.out.println("Sketch save path:" + outFile);
    try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
      long length = FSFactoryProducer.getFSFactory().getFile(filename).length();
      printlnBoth(pw,
          "-------------------------------- TsFile Sketch --------------------------------");
      printlnBoth(pw, "file path: " + filename);
      printlnBoth(pw, "file length: " + length);

      // get metadata information
      TsFileSequenceReader reader = new TsFileSequenceReader(filename);
      TsFileMetadata tsFileMetaData = reader.readFileMetadata();
      List<String> devices = reader.getAllDevices();
      Map<String, Map<String, List<ChunkMetadata>>> tsDeviceSeriesMetadataMap = new LinkedHashMap<>();
      for (String deviceId : devices) {
        Map<String, List<ChunkMetadata>> seriesMetadataMap = reader
            .readChunkMetadataInDevice(deviceId);
        tsDeviceSeriesMetadataMap.put(deviceId, seriesMetadataMap);
      }

      // begin print
      StringBuilder str1 = new StringBuilder();
      for (int i = 0; i < 21; i++) {
        str1.append("|");
      }

      printlnBoth(pw, "");
      printlnBoth(pw, String.format("%20s", "POSITION") + "|\tCONTENT");
      printlnBoth(pw, String.format("%20s", "--------") + " \t-------");
      printlnBoth(pw, String.format("%20d", 0) + "|\t[magic head] " + reader.readHeadMagic());
      printlnBoth(pw,
          String.format("%20d", TSFileConfig.MAGIC_STRING.getBytes().length)
              + "|\t[version number] "
              + reader.readVersionNumber());
      // device begins
      for (Entry<String, Map<String, List<ChunkMetadata>>> entry : tsDeviceSeriesMetadataMap
          .entrySet()) {
        printlnBoth(pw, str1.toString() + "\t[Chunks] of " + entry.getKey() +
            ", num of Chunks:" + entry.getValue().size());
        // chunk begins
        long chunkEndPos = 0;
        for (Entry<String, List<ChunkMetadata>> seriesMetadata : entry.getValue().entrySet()) {
          for (ChunkMetadata chunkMetaData : seriesMetadata.getValue()) {
            printlnBoth(pw,
                String.format("%20d", chunkMetaData.getOffsetOfChunkHeader()) + "|\t[Chunk] of "
                    + chunkMetaData.getMeasurementUid() + ", numOfPoints:" + chunkMetaData
                    .getNumOfPoints() + ", time range:[" + chunkMetaData.getStartTime() + ","
                    + chunkMetaData.getEndTime() + "], tsDataType:" + chunkMetaData.getDataType()
                    + ", \n" + String.format("%20s", "") + " \t" + chunkMetaData.getStatistics());
            printlnBoth(pw, String.format("%20s", "") + "|\t\t[marker] 1");
            printlnBoth(pw, String.format("%20s", "") + "|\t\t[ChunkHeader]");
            Chunk chunk = reader.readMemChunk(chunkMetaData);
            printlnBoth(pw,
                String.format("%20s", "") + "|\t\t" + chunk.getHeader().getNumOfPages() + " pages");
            chunkEndPos =
                chunkMetaData.getOffsetOfChunkHeader() + chunk.getHeader().getSerializedSize()
                    + chunk.getHeader().getDataSize();
          }
        }
        // chunkGroupFooter begins
        printlnBoth(pw, String.format("%20s", chunkEndPos) + "|\t[Chunk Group Footer]");
        ChunkGroupFooter chunkGroupFooter = reader.readChunkGroupFooter(chunkEndPos, false);
        printlnBoth(pw, String.format("%20s", "") + "|\t\t[marker] 0");
        printlnBoth(pw,
            String.format("%20s", "") + "|\t\t[deviceID] " + chunkGroupFooter.getDeviceID());
        printlnBoth(pw,
            String.format("%20s", "") + "|\t\t[dataSize] " + chunkGroupFooter.getDataSize());
        printlnBoth(pw, String.format("%20s", "") + "|\t\t[num of chunks] " + chunkGroupFooter
            .getNumberOfChunks());
        printlnBoth(pw, str1.toString() + "\t[Chunks] of "
            + entry.getKey() + " ends");
      }

      // metadata begins
      if (tsFileMetaData.getMetadataIndex().getChildren().isEmpty()) {
        printlnBoth(pw, String.format("%20s", reader.getFileMetadataPos() - 1) + "|\t[marker] 2");
      } else {
        printlnBoth(pw,
            String.format("%20s", reader.readFileMetadata().getMetaOffset() + "|\t[marker] 2"));
      }
      for (MetadataIndexEntry metadataIndex : tsFileMetaData.getMetadataIndex().getChildren()) {
        printlnBoth(pw, String.format("%20s", metadataIndex.getOffset())
            + "|\t[MetadataIndex] of " + metadataIndex.getName());
      }

      printlnBoth(pw, String.format("%20s", reader.getFileMetadataPos()) + "|\t[TsFileMetaData]");
      printlnBoth(pw, String.format("%20s", "") + "|\t\t[num of devices] " + tsFileMetaData
          .getMetadataIndex().getChildren().size());
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t" + tsFileMetaData.getMetadataIndex().getChildren()
              .size() + " key&TsMetadataIndex");
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t[totalChunkNum] " + tsFileMetaData.getTotalChunkNum());
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t[invalidChunkNum] " + tsFileMetaData
              .getInvalidChunkNum());

      // bloom filter
      BloomFilter bloomFilter = tsFileMetaData.getBloomFilter();
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t[bloom filter bit vector byte array length] "
              + bloomFilter.serialize().length);
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t[bloom filter bit vector byte array] ");
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t[bloom filter number of bits] "
              + bloomFilter.getSize());
      printlnBoth(pw,
          String.format("%20s", "") + "|\t\t[bloom filter number of hash functions] "
              + bloomFilter.getHashFunctionSize());

      printlnBoth(pw,
          String.format("%20s", (reader.getFileMetadataPos() + reader.getFileMetadataSize()))
              + "|\t[TsFileMetaDataSize] " + reader.getFileMetadataSize());

      printlnBoth(pw,
          String.format("%20s", reader.getFileMetadataPos() + reader.getFileMetadataSize() + 4)
              + "|\t[magic tail] " + reader.readTailMagic());

      printlnBoth(pw,
          String.format("%20s", length) + "|\tEND of TsFile");

      printlnBoth(pw, "");

      printlnBoth(pw,
          "---------------------------------- TsFile Sketch End ----------------------------------");
    }
  }

  private static void printlnBoth(PrintWriter pw, String str) {
    System.out.println(str);
    pw.println(str);
  }

}
