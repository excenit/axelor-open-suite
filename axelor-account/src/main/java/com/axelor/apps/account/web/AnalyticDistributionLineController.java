/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.analytic.AnalyticMoveLineService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;

@Singleton
public class AnalyticDistributionLineController {

  public void computeAmount(ActionRequest request, ActionResponse response) {
    try {
      AnalyticMoveLine analyticMoveLine = request.getContext().asType(AnalyticMoveLine.class);
      response.setValue(
          "amount", Beans.get(AnalyticMoveLineService.class).computeAmount(analyticMoveLine));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validateLines(ActionRequest request, ActionResponse response) {
    try {
      AnalyticDistributionTemplate analyticDistributionTemplate =
          request.getContext().asType(AnalyticDistributionTemplate.class);
      if (!Beans.get(AnalyticMoveLineService.class)
          .validateLines(analyticDistributionTemplate.getAnalyticDistributionLineList())) {
        response.setError(
            I18n.get(
                "The configured distribution is incorrect, the sum of percentages for at least an axis is different than 100%"));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void manageNewAnalyticDistributionLine(ActionRequest request, ActionResponse response)
      throws AxelorException {
    try {
      Context parent = request.getContext().getParent();
      if (MoveLine.class.toString().equals(parent.getContextClass().toString())) {
        MoveLine line = parent.asType(MoveLine.class);
        response.setValue(
            "analyticJournal",
            Beans.get(AccountConfigService.class)
                .getAccountConfig(line.getAccount().getCompany())
                .getAnalyticJournal());
        if (line.getDate() != null) {
          response.setValue("date", line.getDate());
        } else {
          response.setValue(
              "date", Beans.get(AppBaseService.class).getTodayDate(line.getAccount().getCompany()));
        }
      } else if (InvoiceLine.class.toString().equals(parent.getContextClass().toString())) {
        InvoiceLine line = request.getContext().getParent().asType(InvoiceLine.class);
        response.setValue(
            "analyticJournal",
            Beans.get(AccountConfigService.class)
                .getAccountConfig(line.getAccount().getCompany())
                .getAnalyticJournal());
        response.setValue(
            "date", Beans.get(AppBaseService.class).getTodayDate(line.getAccount().getCompany()));
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void calculateAmountWithPercentage(ActionRequest request, ActionResponse response)
      throws AxelorException {
    try {
      AnalyticMoveLine analyticMoveLine = request.getContext().asType(AnalyticMoveLine.class);
      Context parent = request.getContext().getParent();
      if (MoveLine.class.toString().equals(parent.getContextClass().toString())) {
        MoveLine line = parent.asType(MoveLine.class);
        if (analyticMoveLine != null && line != null) {
          response.setValue(
              "amount",
              Beans.get(AnalyticMoveLineService.class).getAnalyticAmount(line, analyticMoveLine));
        }
      } else if (InvoiceLine.class.toString().equals(parent.getContextClass().toString())) {
        InvoiceLine line = parent.asType(InvoiceLine.class);
        if (analyticMoveLine != null && line != null) {
          response.setValue(
              "amount",
              Beans.get(AnalyticMoveLineService.class).getAnalyticAmount(line, analyticMoveLine));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
