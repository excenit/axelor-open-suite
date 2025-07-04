/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.db.repo;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.service.MetaFileService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.auth.db.User;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.google.common.collect.Lists;
import java.util.List;
import javax.persistence.PersistenceException;
import org.apache.commons.collections.CollectionUtils;

public class PartnerBaseRepository extends PartnerRepository {

  @Override
  public Partner save(Partner partner) {
    try {
      Beans.get(PartnerService.class).onSave(partner);
      List<PartnerAddress> partnerAddressList = partner.getPartnerAddressList();
      if (CollectionUtils.isNotEmpty(partnerAddressList) && partnerAddressList.size() == 1) {
        PartnerAddress partnerAddress = partnerAddressList.get(0);
        partnerAddress.setIsDefaultAddr(true);
        partnerAddress.setIsDeliveryAddr(true);
        partnerAddress.setIsInvoicingAddr(true);
      }
      return super.save(partner);
    } catch (Exception e) {
      TraceBackService.traceExceptionFromSaveMethod(e);
      throw new PersistenceException(e.getMessage(), e);
    }
  }

  @Override
  public Partner copy(Partner partner, boolean deep) {

    Partner copy = super.copy(partner, deep);

    copy.setPartnerSeq(null);
    copy.setEmailAddress(null);

    try {
      MetaFile picture = copy.getPicture();
      if (picture != null) {
        MetaFile pictureCopy = Beans.get(MetaFileService.class).copyMetaFile(picture);
        copy.setPicture(pictureCopy);
      }
    } catch (Exception e) {
      throw new PersistenceException(e);
    }

    copy.setPartnerAddressList(Lists.newArrayList());
    copy.setBlockingList(null);
    copy.setBankDetailsList(null);

    return copy;
  }

  @Override
  public void remove(Partner partner) {
    if (partner.getLinkedUser() != null) {
      UserBaseRepository userRepo = Beans.get(UserBaseRepository.class);
      User user = userRepo.find(partner.getLinkedUser().getId());
      if (user != null) {
        user.setPartner(null);
        userRepo.save(user);
      }
    }

    super.remove(partner);
  }
}
