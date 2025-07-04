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

import com.axelor.apps.base.db.ProductCompany;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.inject.Beans;
import java.util.Map;

public class ProductCompanyBaseRepository extends ProductCompanyRepository {
  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    json.put(
        "$nbDecimalDigitForUnitPrice",
        Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice());

    return super.populate(json, context);
  }

  @Override
  public ProductCompany copy(ProductCompany productCompany, boolean deep) {
    ProductCompany copy = super.copy(productCompany, deep);
    Beans.get(ProductService.class).copyProduct(productCompany, copy);
    return copy;
  }
}
