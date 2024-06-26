package com.axelor.apps.intervention.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.DurationService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.repo.ContractLineRepository;
import com.axelor.apps.contract.db.repo.ContractRepository;
import com.axelor.apps.contract.db.repo.ContractVersionRepository;
import com.axelor.apps.contract.service.ContractLineService;
import com.axelor.apps.contract.service.ContractServiceImpl;
import com.axelor.apps.contract.service.ContractVersionService;
import com.axelor.apps.intervention.db.Equipment;
import com.axelor.apps.intervention.repo.EquipmentRepository;
import com.axelor.apps.supplychain.service.PartnerLinkSupplychainService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.List;

public class ContractInterventionServiceImpl extends ContractServiceImpl {

  protected EquipmentRepository equipmentRepository;

  @Inject
  public ContractInterventionServiceImpl(
      ContractLineService contractLineService,
      ContractVersionService contractVersionService,
      SequenceService sequenceService,
      ContractVersionRepository contractVersionRepository,
      AppBaseService appBaseService,
      ContractVersionService versionService,
      DurationService durationService,
      ContractLineRepository contractLineRepo,
      ContractRepository contractRepository,
      PartnerLinkSupplychainService partnerLinkSupplychainService,
      EquipmentRepository equipmentRepository) {
    super(
        contractLineService,
        contractVersionService,
        sequenceService,
        contractVersionRepository,
        appBaseService,
        versionService,
        durationService,
        contractLineRepo,
        contractRepository,
        partnerLinkSupplychainService);
    this.equipmentRepository = equipmentRepository;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice ongoingCurrentVersion(Contract contract, LocalDate date) throws AxelorException {
    Invoice invoice = super.ongoingCurrentVersion(contract, date);

    List<Equipment> equipmentList =
        equipmentRepository
            .all()
            .filter("self.contract.id = :contractId")
            .bind("contractId", contract.getId())
            .fetch();
    equipmentList.forEach(equipment -> equipmentRepository.save(equipment));

    return invoice;
  }
}
