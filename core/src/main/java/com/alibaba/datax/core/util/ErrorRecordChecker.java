package com.alibaba.datax.core.util;

import static com.alibaba.datax.core.util.FrameworkErrorCode.PLUGIN_DIRTY_DATA_LIMIT_EXCEED;
import static com.alibaba.datax.core.util.container.CoreConstant.DATAX_JOB_SETTING_ERRORLIMIT_PERCENT;
import static com.alibaba.datax.core.util.container.CoreConstant.DATAX_JOB_SETTING_ERRORLIMIT_RECORD;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.core.statistics.communication.Communication;
import com.alibaba.datax.core.statistics.communication.CommunicationTool;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 检查任务是否到达错误记录限制。有检查条数（recordLimit）和百分比(percentageLimit)两种方式。 <br>
 * 1. errorRecord表示出错条数不能大于限制数，当超过时任务失败。比如errorRecord为0表示不容许任何脏数据。 <br>
 * 2. errorPercentage表示出错比例，在任务结束时校验。  <br>
 * 3. errorRecord优先级高于errorPercentage。 <br>
 */
public final class ErrorRecordChecker {

  private static final Logger LOG = LoggerFactory.getLogger(ErrorRecordChecker.class);
  /**
   * 错误行限制数
   */
  private Long recordLimit;
  /**
   * 错误行百分比显示数
   */
  private Double percentageLimit;

  /**
   * 构造器1 从配置中 进行 容错限制检查（容错行数+容错百分比）
   *
   * @param configuration Configuration
   */
  public ErrorRecordChecker(Configuration configuration) {
    this(configuration.getLong(DATAX_JOB_SETTING_ERRORLIMIT_RECORD),
        configuration.getDouble(DATAX_JOB_SETTING_ERRORLIMIT_PERCENT));
  }

  /**
   * 构造器2
   *
   * @param rec        Long
   * @param percentage Double
   */
  public ErrorRecordChecker(Long rec, Double percentage) {
    recordLimit = rec;
    percentageLimit = percentage;

    if (percentageLimit != null) {
      Validate.isTrue(0.0 <= percentageLimit && percentageLimit <= 1.0,
          "脏数据百分比限制应该在[0.0, 1.0]之间");
    }

    if (recordLimit != null) {
      Validate.isTrue(recordLimit >= 0, "脏数据条数现在应该为非负整数");
      // errorRecord优先级高于errorPercentage.
      percentageLimit = null;
    }
  }


  public void checkRecordLimit(Communication communication) {
    if (recordLimit == null) {
      return;
    }

    long errorNumber = CommunicationTool.getTotalErrorRecords(communication);
    if (recordLimit < errorNumber) {
      LOG.debug(String.format("Error-limit set to %d, error count check.", recordLimit));
      throw DataXException.asDataXException(PLUGIN_DIRTY_DATA_LIMIT_EXCEED,
          String.format("脏数据条数检查不通过，限制是[%d]条，但实际上捕获了[%d]条.", recordLimit, errorNumber));
    }
  }

  public void checkPercentageLimit(Communication communication) {
    if (percentageLimit == null) {
      return;
    }
    LOG.debug(String.format("Error-limit set to %f, error percent check.", percentageLimit));

    long total = CommunicationTool.getTotalReadRecords(communication);
    long error = CommunicationTool.getTotalErrorRecords(communication);

    if (total > 0) {
      double actualPer = (double) error / (double) total;
      if (actualPer > percentageLimit) {
        throw DataXException.asDataXException(PLUGIN_DIRTY_DATA_LIMIT_EXCEED,
            String.format("脏数据百分比检查不通过，限制是[%f]，但实际上捕获到[%f].", percentageLimit, actualPer));
      }
    }
  }
}
