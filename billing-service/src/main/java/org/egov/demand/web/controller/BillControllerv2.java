package org.egov.demand.web.controller;

import javax.validation.Valid;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.demand.helper.BillHelperV2;
import org.egov.demand.model.BillSearchCriteria;
import org.egov.demand.model.GenerateBillCriteria;
import org.egov.demand.service.BillServicev2;
import org.egov.demand.web.contract.BillRequestV2;
import org.egov.demand.web.contract.BillResponseV2;
import org.egov.demand.web.contract.RequestInfoWrapper;
import org.egov.demand.web.contract.factory.ResponseFactory;
import org.egov.demand.web.validator.BillValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("bill/v2/")
public class BillControllerv2 {
	
	@Autowired
	private BillServicev2 billService;
	
	@Autowired
	private BillValidator billValidator;
	
	@Autowired
	private ResponseFactory responseFactory;
	
	@Autowired
	private BillHelperV2 billHelper;
	
	@PostMapping("_search")
	@ResponseBody
	public ResponseEntity<?> search(@RequestBody @Valid final RequestInfoWrapper requestInfoWrapper,
			@ModelAttribute @Valid final BillSearchCriteria billCriteria,
			final BindingResult bindingResult) {
		billValidator.validateBillSearchCriteria(billCriteria, bindingResult);
		RequestInfo requestInfo = requestInfoWrapper.getRequestInfo();
		if (bindingResult.hasErrors()) {
			final ErrorResponse errorResponse = responseFactory.getErrorResponse(bindingResult, requestInfo);
			return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(billService.searchBill(billCriteria,requestInfo), HttpStatus.OK);
	}


	@PostMapping("_fetchbill")
	@ResponseBody
	public ResponseEntity<?> fetchBill(@RequestBody RequestInfoWrapper requestInfoWrapper, 
			@ModelAttribute @Valid GenerateBillCriteria generateBillCriteria){
		
		BillResponseV2 billResponse = billService.fetchBill(generateBillCriteria, requestInfoWrapper);
		return new ResponseEntity<>(billResponse, HttpStatus.CREATED);
	}
	
	
	@PostMapping("_generate")
	@ResponseBody
	public ResponseEntity<?> genrateBill(@RequestBody RequestInfoWrapper requestInfoWrapper,
			@ModelAttribute @Valid GenerateBillCriteria generateBillCriteria) {

		billValidator.validateBillGenRequest(generateBillCriteria);
		BillResponseV2 billResponse = billService.generateBill(generateBillCriteria, requestInfoWrapper.getRequestInfo());
		return new ResponseEntity<>(billResponse, HttpStatus.CREATED);
	}
	
	@PostMapping("_create")
	@ResponseBody
	public ResponseEntity<?> create(@RequestBody BillRequestV2 billRequest, BindingResult bindingResult){

		
		if (bindingResult.hasErrors()) {
			return new ResponseEntity<>(responseFactory.
					getErrorResponse(bindingResult, billRequest.getRequestInfo()), HttpStatus.BAD_REQUEST);
		}
		BillResponseV2 billResponse = billService.sendBillToKafka(billRequest);
		billHelper.getBillRequestWithIds(billRequest);
		return new ResponseEntity<>(billResponse,HttpStatus.CREATED);
	}
}
