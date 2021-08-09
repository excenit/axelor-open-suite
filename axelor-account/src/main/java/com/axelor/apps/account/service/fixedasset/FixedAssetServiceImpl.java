/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.service.fixedasset;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.FixedAsset;
import com.axelor.apps.account.db.FixedAssetCategory;
import com.axelor.apps.account.db.FixedAssetDerogatoryLine;
import com.axelor.apps.account.db.FixedAssetLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.FixedAssetCategoryRepository;
import com.axelor.apps.account.db.repo.FixedAssetLineRepository;
import com.axelor.apps.account.db.repo.FixedAssetRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AnalyticFixedAssetService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class FixedAssetServiceImpl implements FixedAssetService {

  protected FixedAssetRepository fixedAssetRepo;

  protected FixedAssetLineMoveService fixedAssetLineMoveService;

  protected FixedAssetLineComputationService fixedAssetLineComputationService;

  protected MoveLineService moveLineService;

  protected AccountConfigService accountConfigService;

  protected FixedAssetDerogatoryLineService fixedAssetDerogatoryLineService;

  protected AnalyticFixedAssetService analyticFixedAssetService;

  protected static final int CALCULATION_SCALE = 20;
  protected static final int RETURNED_SCALE = 2;

  @Inject
  public FixedAssetServiceImpl(
      FixedAssetRepository fixedAssetRepo,
      FixedAssetLineMoveService fixedAssetLineMoveService,
      FixedAssetLineComputationService fixedAssetLineComputationService,
      MoveLineService moveLineService,
      AccountConfigService accountConfigService,
      FixedAssetDerogatoryLineService fixedAssetDerogatoryLineService,
      AnalyticFixedAssetService analyticFixedAssetService) {
    this.fixedAssetRepo = fixedAssetRepo;
    this.fixedAssetLineMoveService = fixedAssetLineMoveService;
    this.fixedAssetLineComputationService = fixedAssetLineComputationService;
    this.moveLineService = moveLineService;
    this.accountConfigService = accountConfigService;
    this.fixedAssetDerogatoryLineService = fixedAssetDerogatoryLineService;
    this.analyticFixedAssetService = analyticFixedAssetService;
    ;
  }

  @Override
  public FixedAsset generateAndComputeLines(FixedAsset fixedAsset) {

    generateAndComputeFixedAssetLines(fixedAsset);
    generateAndComputeFiscalFixedAssetLines(fixedAsset);

    generateAndComputeFixedAssetDerogatoryLines(fixedAsset);

    return fixedAsset;
  }

  @Override
  public void generateAndComputeFixedAssetDerogatoryLines(FixedAsset fixedAsset) {
    if (fixedAsset
        .getDepreciationPlanSelect()
        .contains(FixedAssetRepository.DEPRECIATION_PLAN_DEROGATION)) {

      List<FixedAssetDerogatoryLine> fixedAssetDerogatoryLineList =
          fixedAssetDerogatoryLineService.computeFixedAssetDerogatoryLineList(fixedAsset);
      if (fixedAssetDerogatoryLineList.size() != 0) {
        if (fixedAsset.getFixedAssetDerogatoryLineList() == null) {
          fixedAsset.setFixedAssetDerogatoryLineList(new ArrayList<>());
          fixedAsset.getFixedAssetDerogatoryLineList().addAll(fixedAssetDerogatoryLineList);
        } else {
          List<FixedAssetDerogatoryLine> linesToKeep =
              fixedAsset.getFixedAssetDerogatoryLineList().stream()
                  .filter(
                      line -> line.getStatusSelect() == FixedAssetLineRepository.STATUS_REALIZED)
                  .collect(Collectors.toList());
          fixedAsset.clearFixedAssetDerogatoryLineList();
          fixedAsset.getFixedAssetDerogatoryLineList().addAll(linesToKeep);
          fixedAsset.getFixedAssetDerogatoryLineList().addAll(fixedAssetDerogatoryLineList);
        }
      }
    }
  }

  @Override
  public void generateAndComputeFiscalFixedAssetLines(FixedAsset fixedAsset) {
    if (fixedAsset
        .getDepreciationPlanSelect()
        .contains(FixedAssetRepository.DEPRECIATION_PLAN_FISCAL)) {
      FixedAssetLine initialFiscalFixedAssetLine =
          fixedAssetLineComputationService.computeInitialPlannedFixedAssetLine(
              fixedAsset, FixedAssetLineRepository.TYPE_SELECT_FISCAL);
      fixedAsset.addFiscalFixedAssetLineListItem(initialFiscalFixedAssetLine);

      generateComputedPlannedFixedAssetLine(
          fixedAsset,
          initialFiscalFixedAssetLine,
          fixedAsset.getFiscalFixedAssetLineList(),
          FixedAssetLineRepository.TYPE_SELECT_FISCAL);
    }
  }

  @Override
  public void generateAndComputeFixedAssetLines(FixedAsset fixedAsset) {
    if (fixedAsset
        .getDepreciationPlanSelect()
        .contains(FixedAssetRepository.DEPRECIATION_PLAN_ECONOMIC)) {
      FixedAssetLine initialFixedAssetLine =
          fixedAssetLineComputationService.computeInitialPlannedFixedAssetLine(
              fixedAsset, FixedAssetLineRepository.TYPE_SELECT_ECONOMIC);
      fixedAsset.addFixedAssetLineListItem(initialFixedAssetLine);

      generateComputedPlannedFixedAssetLine(
          fixedAsset,
          initialFixedAssetLine,
          fixedAsset.getFixedAssetLineList(),
          FixedAssetLineRepository.TYPE_SELECT_ECONOMIC);
    }
  }

  private List<FixedAssetLine> generateComputedPlannedFixedAssetLine(
      FixedAsset fixedAsset,
      FixedAssetLine initialFixedAssetLine,
      List<FixedAssetLine> fixedAssetLineList,
      int typeSelect) {

    // counter to avoid too many iterations in case of a current or future mistake
    int c = 0;
    final int MAX_ITERATION = 1000;
    FixedAssetLine fixedAssetLine = initialFixedAssetLine;
    while (c < MAX_ITERATION && fixedAssetLine.getAccountingValue().signum() != 0) {
      fixedAssetLine =
          fixedAssetLineComputationService.computePlannedFixedAssetLine(
              fixedAsset, fixedAssetLine, typeSelect);
      fixedAssetLineList.add(fixedAssetLine);
      c++;
    }

    return fixedAssetLineList;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public List<FixedAsset> createFixedAssets(Invoice invoice) throws AxelorException {

    List<FixedAsset> fixedAssetList = new ArrayList<>();
    if (invoice == null || CollectionUtils.isEmpty(invoice.getInvoiceLineList())) {
      return fixedAssetList;
    }

    AccountConfig accountConfig = accountConfigService.getAccountConfig(invoice.getCompany());

    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

      if (accountConfig.getFixedAssetCatReqOnInvoice()
          && invoiceLine.getFixedAssets()
          && invoiceLine.getFixedAssetCategory() == null) {
        throw new AxelorException(
            invoiceLine,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.INVOICE_LINE_ERROR_FIXED_ASSET_CATEGORY),
            invoiceLine.getProductName());
      }

      if (!invoiceLine.getFixedAssets() || invoiceLine.getFixedAssetCategory() == null) {
        continue;
      }

      FixedAsset fixedAsset = new FixedAsset();
      fixedAsset.setFixedAssetCategory(invoiceLine.getFixedAssetCategory());
      if (fixedAsset.getFixedAssetCategory().getIsValidateFixedAsset()) {
        fixedAsset.setStatusSelect(FixedAssetRepository.STATUS_VALIDATED);
      } else {
        fixedAsset.setStatusSelect(FixedAssetRepository.STATUS_DRAFT);
      }
      fixedAsset.setAcquisitionDate(invoice.getInvoiceDate());
      fixedAsset.setFirstDepreciationDate(invoice.getInvoiceDate());
      fixedAsset.setReference(invoice.getInvoiceId());
      fixedAsset.setName(invoiceLine.getProductName() + " (" + invoiceLine.getQty() + ")");
      fixedAsset.setCompany(fixedAsset.getFixedAssetCategory().getCompany());
      fixedAsset.setJournal(fixedAsset.getFixedAssetCategory().getJournal());
      fixedAsset.setComputationMethodSelect(
          fixedAsset.getFixedAssetCategory().getComputationMethodSelect());
      fixedAsset.setDegressiveCoef(fixedAsset.getFixedAssetCategory().getDegressiveCoef());
      fixedAsset.setNumberOfDepreciation(
          fixedAsset.getFixedAssetCategory().getNumberOfDepreciation());
      fixedAsset.setPeriodicityInMonth(fixedAsset.getFixedAssetCategory().getPeriodicityInMonth());
      fixedAsset.setDurationInMonth(fixedAsset.getFixedAssetCategory().getDurationInMonth());
      fixedAsset.setGrossValue(invoiceLine.getCompanyExTaxTotal());
      fixedAsset.setPartner(invoice.getPartner());
      fixedAsset.setPurchaseAccount(invoiceLine.getAccount());
      fixedAsset.setInvoiceLine(invoiceLine);

      this.generateAndComputeLines(fixedAsset);

      fixedAssetList.add(fixedAssetRepo.save(fixedAsset));
    }
    return fixedAssetList;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void disposal(LocalDate disposalDate, BigDecimal disposalAmount, FixedAsset fixedAsset)
      throws AxelorException {

    Map<Integer, List<FixedAssetLine>> fixedAssetLineMap =
        fixedAsset.getFixedAssetLineList().stream()
            .collect(Collectors.groupingBy(FixedAssetLine::getStatusSelect));
    List<FixedAssetLine> previousPlannedLineList =
        fixedAssetLineMap.get(FixedAssetLineRepository.STATUS_PLANNED);
    List<FixedAssetLine> previousRealizedLineList =
        fixedAssetLineMap.get(FixedAssetLineRepository.STATUS_REALIZED);
    FixedAssetLine previousPlannedLine =
        previousPlannedLineList != null && !previousPlannedLineList.isEmpty()
            ? previousPlannedLineList.get(0)
            : null;
    FixedAssetLine previousRealizedLine =
        previousRealizedLineList != null && !previousRealizedLineList.isEmpty()
            ? previousRealizedLineList.get(previousRealizedLineList.size() - 1)
            : null;

    if (previousPlannedLine != null
        && disposalDate.isAfter(previousPlannedLine.getDepreciationDate())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.FIXED_ASSET_DISPOSAL_DATE_ERROR_2));
    }

    if (previousRealizedLine != null
        && disposalDate.isBefore(previousRealizedLine.getDepreciationDate())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.FIXED_ASSET_DISPOSAL_DATE_ERROR_1));
    }

    if (disposalAmount.compareTo(BigDecimal.ZERO) != 0) {

      FixedAssetLine depreciationFixedAssetLine =
          generateProrataDepreciationLine(fixedAsset, disposalDate, previousRealizedLine);
      fixedAssetLineMoveService.realize(depreciationFixedAssetLine);
      fixedAssetLineMoveService.generateDisposalMove(depreciationFixedAssetLine);
    } else {
      if (disposalAmount.compareTo(fixedAsset.getResidualValue()) != 0) {
        return;
      }
    }
    List<FixedAssetLine> fixedAssetLineList =
        fixedAsset.getFixedAssetLineList().stream()
            .filter(
                fixedAssetLine ->
                    fixedAssetLine.getStatusSelect() == FixedAssetLineRepository.STATUS_PLANNED)
            .collect(Collectors.toList());
    for (FixedAssetLine fixedAssetLine : fixedAssetLineList) {
      fixedAsset.removeFixedAssetLineListItem(fixedAssetLine);
    }
    fixedAsset.setStatusSelect(FixedAssetRepository.STATUS_TRANSFERRED);
    fixedAsset.setDisposalDate(disposalDate);
    fixedAsset.setDisposalValue(disposalAmount);
    fixedAssetRepo.save(fixedAsset);
  }

  protected FixedAssetLine generateProrataDepreciationLine(
      FixedAsset fixedAsset, LocalDate disposalDate, FixedAssetLine previousRealizedLine) {

    LocalDate previousRealizedDate =
        previousRealizedLine != null
            ? previousRealizedLine.getDepreciationDate()
            : fixedAsset.getFirstDepreciationDate();
    long monthsBetweenDates =
        ChronoUnit.MONTHS.between(
            previousRealizedDate.withDayOfMonth(1), disposalDate.withDayOfMonth(1));

    FixedAssetLine fixedAssetLine = new FixedAssetLine();
    fixedAssetLine.setDepreciationDate(disposalDate);
    BigDecimal prorataTemporis =
        BigDecimal.valueOf(monthsBetweenDates)
            .divide(
                BigDecimal.valueOf(fixedAsset.getPeriodicityInMonth()),
                CALCULATION_SCALE,
                RoundingMode.HALF_UP);

    int numberOfDepreciation =
        fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
            ? fixedAsset.getNumberOfDepreciation() - 1
            : fixedAsset.getNumberOfDepreciation();
    BigDecimal depreciationRate =
        BigDecimal.valueOf(100)
            .divide(
                BigDecimal.valueOf(numberOfDepreciation), CALCULATION_SCALE, RoundingMode.HALF_UP);
    BigDecimal ddRate = BigDecimal.ONE;
    if (fixedAsset
        .getComputationMethodSelect()
        .equals(FixedAssetRepository.COMPUTATION_METHOD_DEGRESSIVE)) {
      ddRate = fixedAsset.getDegressiveCoef();
    }
    BigDecimal deprecationValue =
        fixedAsset
            .getGrossValue()
            .multiply(depreciationRate)
            .multiply(ddRate)
            .multiply(prorataTemporis)
            .divide(new BigDecimal(100), RETURNED_SCALE, RoundingMode.HALF_UP);

    fixedAssetLine.setDepreciation(deprecationValue);
    BigDecimal cumulativeValue =
        previousRealizedLine != null
            ? previousRealizedLine.getCumulativeDepreciation().add(deprecationValue)
            : deprecationValue;
    fixedAssetLine.setCumulativeDepreciation(cumulativeValue);
    fixedAssetLine.setResidualValue(
        fixedAsset.getGrossValue().subtract(fixedAssetLine.getCumulativeDepreciation()));
    fixedAsset.addFixedAssetLineListItem(fixedAssetLine);
    return fixedAssetLine;
  }

  @Override
  @Transactional
  public void createAnalyticOnMoveLine(
      AnalyticDistributionTemplate analyticDistributionTemplate, MoveLine moveLine)
      throws AxelorException {
    if (analyticDistributionTemplate != null
        && moveLine.getAccount().getAnalyticDistributionAuthorized()) {
      moveLine.setAnalyticDistributionTemplate(analyticDistributionTemplate);
      moveLine = moveLineService.createAnalyticDistributionWithTemplate(moveLine);
    }
  }

  @Override
  public void updateAnalytic(FixedAsset fixedAsset) throws AxelorException {
    if (fixedAsset.getAnalyticDistributionTemplate() != null) {
      if (fixedAsset.getDisposalMove() != null) {
        for (MoveLine moveLine : fixedAsset.getDisposalMove().getMoveLineList()) {
          this.createAnalyticOnMoveLine(fixedAsset.getAnalyticDistributionTemplate(), moveLine);
        }
      }
      if (fixedAsset.getFixedAssetLineList() != null) {
        for (FixedAssetLine fixedAssetLine : fixedAsset.getFixedAssetLineList()) {
          if (fixedAssetLine.getDepreciationAccountMove() != null) {
            for (MoveLine moveLine :
                fixedAssetLine.getDepreciationAccountMove().getMoveLineList()) {
              this.createAnalyticOnMoveLine(fixedAsset.getAnalyticDistributionTemplate(), moveLine);
            }
          }
        }
      }
    }
  }

  /**
   * If firstDepreciationDateInitSeelct if acquisition Date THEN : -If PeriodicityTypeSelect = 12
   * (Year) >> FirstDepreciationDate = au 31/12 of the year of fixedAsset.acquisitionDate -if
   * PeriodicityTypeSelect = 1 (Month) >> FirstDepreciationDate = last day of the month of
   * fixedAsset.acquisitionDate Else (== first service date) -If PeriodicityTypeSelect = 12 (Year)
   * >> FirstDepreciationDate = au 31/12 of the year of fixedAsset.firstServiceDate -if
   * PeriodicityTypeSelect = 1 (Month) >> FirstDepreciationDate = last day of the month of
   * fixedAsset.firstServiceDate
   */
  @Override
  public void computeFirstDepreciationDate(FixedAsset fixedAsset) {

    FixedAssetCategory fixedAssetCategory = fixedAsset.getFixedAssetCategory();
    Integer periodicityTypeSelect = fixedAsset.getPeriodicityTypeSelect();
    Integer firstDepreciationDateInitSelect =
        fixedAssetCategory.getFirstDepreciationDateInitSelect();
    if (fixedAssetCategory != null
        && periodicityTypeSelect != null
        && firstDepreciationDateInitSelect != null) {
      if (firstDepreciationDateInitSelect
          == FixedAssetCategoryRepository.REFERENCE_FIRST_DEPRECIATION_DATE_ACQUISITION) {
        fixedAsset.setFirstDepreciationDate(
            analyticFixedAssetService.computeFirstDepreciationDate(
                fixedAsset, fixedAsset.getAcquisitionDate()));
      } else {
        fixedAsset.setFirstDepreciationDate(
            analyticFixedAssetService.computeFirstDepreciationDate(
                fixedAsset, fixedAsset.getFirstServiceDate()));
      }
    }
  }
}