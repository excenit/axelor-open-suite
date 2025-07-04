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
package com.axelor.apps.supplychain.service.saleorder;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.google.inject.Inject;
import java.util.List;

public class SaleOrderAdvancePaymentFetchServiceImpl
    implements SaleOrderAdvancePaymentFetchService {

  protected final InvoiceRepository invoiceRepository;

  @Inject
  public SaleOrderAdvancePaymentFetchServiceImpl(InvoiceRepository invoiceRepository) {
    this.invoiceRepository = invoiceRepository;
  }

  @Override
  public List<Invoice> getAdvancePayments(SaleOrder saleOrder) {
    return invoiceRepository
        .all()
        .filter(
            "self.saleOrder.id = :saleOrderId AND self.operationSubTypeSelect = :operationSubTypeSelect AND self.operationTypeSelect = :operationTypeSelect")
        .bind("saleOrderId", saleOrder.getId())
        .bind("operationSubTypeSelect", InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE)
        .bind("operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
        .fetch();
  }
}
