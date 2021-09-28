package io.mosip.idrepository.identity.controller;

import static io.mosip.idrepository.core.constant.IdRepoConstants.FACE_EXTRACTION_FORMAT;
import static io.mosip.idrepository.core.constant.IdRepoConstants.FINGER_EXTRACTION_FORMAT;
import static io.mosip.idrepository.core.constant.IdRepoConstants.IRIS_EXTRACTION_FORMAT;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.idrepository.core.constant.AuditEvents;
import io.mosip.idrepository.core.constant.AuditModules;
import io.mosip.idrepository.core.constant.IdType;
import io.mosip.idrepository.core.dto.IdRequestDTO;
import io.mosip.idrepository.core.dto.IdResponseDTO;
import io.mosip.idrepository.core.exception.IdRepoAppException;
import io.mosip.idrepository.core.helper.AuditHelper;
import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.core.security.IdRepoSecurityManager;
import io.mosip.idrepository.core.spi.IdRepoDraftService;
import io.mosip.idrepository.core.util.DataValidationUtil;
import io.mosip.idrepository.identity.helper.VidDraftHelper;
import io.mosip.idrepository.identity.validator.IdRequestValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import springfox.documentation.annotations.ApiIgnore;

/**
 * @author Manoj SP
 *
 */
@RestController
@RequestMapping(path = "/draft")
public class IdRepoDraftController {

	private static final String UIN = "UIN";

	private static final String ID_REPO_DRAFT_CONTROLLER = "IdRepoDraftController";

	private final Logger mosipLogger = IdRepoLogger.getLogger(IdRepoDraftController.class);

	@Autowired
	private IdRequestValidator validator;

	@Autowired
	private IdRepoDraftService<IdRequestDTO, IdResponseDTO> draftService;

	@Autowired
	private AuditHelper auditHelper;
	
	@Autowired
	private VidDraftHelper vidDraftHelper;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(validator);
	}
	@PreAuthorize("hasAnyRole(@authorizedRoles.getPostdraftcreateregistrationId())")
	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PostMapping(path = "/create/{registrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> createDraft(@PathVariable String registrationId,
			@RequestParam(name = UIN, required = false) @Nullable String uin)
			throws IdRepoAppException {
		try {
			return new ResponseEntity<>(draftService.createDraft(registrationId, uin), HttpStatus.OK);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.CREATE_DRAFT_REQUEST_RESPONSE,
					registrationId, IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "createDraft", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.CREATE_DRAFT_REQUEST_RESPONSE,
					registrationId, IdType.ID, "Create draft requested");
		}
	}

	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PreAuthorize("hasAnyRole(@authorizedRoles.getPatchdraftupdateregistrationId())")
	@PatchMapping(path = "/update/{registrationId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> updateDraft(@PathVariable String registrationId, @RequestBody IdRequestDTO request,
			@ApiIgnore Errors errors) throws IdRepoAppException {
		try {
			request.getRequest().setRegistrationId(registrationId);
			validator.validateRequest(request.getRequest(), errors, "update");
			DataValidationUtil.validate(errors);
			return new ResponseEntity<>(draftService.updateDraft(registrationId, request), HttpStatus.OK);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.UPDATE_DRAFT_REQUEST_RESPONSE,
					registrationId, IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "updateDraft", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.CREATE_DRAFT_REQUEST_RESPONSE,
					registrationId, IdType.ID, "Update draft requested");
		}
	}

	@SuppressWarnings({ "unchecked" })
	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PreAuthorize("hasAnyRole(@authorizedRoles.getGetdraftpublishregistrationId())")
	@GetMapping(path = "/publish/{registrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> publishDraft(@PathVariable String registrationId) throws IdRepoAppException {
		try {
			IdResponseDTO publishDraftResponse = draftService.publishDraft(registrationId);
			Map<String, Optional<String>> metadata = (Map<String, Optional<String>>) publishDraftResponse.getMetadata();
			String vid = ((Optional<String>) metadata.get("vid")).orElse(null);
			vidDraftHelper.activateDraftVid(Objects.nonNull(vid) ? (String) vid : null);
			return new ResponseEntity<>(publishDraftResponse, HttpStatus.OK);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.PUBLISH_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "publishDraft", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.CREATE_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, "Publish draft requested");
		}
	}

	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PreAuthorize("hasAnyRole(@authorizedRoles.getDeletedraftdiscardregistrationId())")
	@DeleteMapping(path = "/discard/{registrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> discardDraft(@PathVariable String registrationId) throws IdRepoAppException {
		try {
			return new ResponseEntity<>(draftService.discardDraft(registrationId), HttpStatus.OK);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.DISCARD_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "discardDraft", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.DISCARD_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, "Discard draft requested");
		}
	}

	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PreAuthorize("hasAnyRole(@authorizedRoles.getdraftregistrationId())")
	@RequestMapping(method = RequestMethod.HEAD, path = "/{registrationId}")
	public ResponseEntity<Void> hasDraft(@PathVariable String registrationId) throws IdRepoAppException {
		try {
			HttpStatus responseStatus = draftService.hasDraft(registrationId) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
			return new ResponseEntity<Void>(responseStatus);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.HAS_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "hasDraft", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.HAS_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, "Has draft requested");
		}
	}

	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PreAuthorize("hasAnyRole(@authorizedRoles.getGetdraftregistrationId())")
	@GetMapping(path = "/{registrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> getDraft(@PathVariable String registrationId,
			@RequestParam(name = FINGER_EXTRACTION_FORMAT, required = false) @Nullable String fingerExtractionFormat,
			@RequestParam(name = IRIS_EXTRACTION_FORMAT, required = false) @Nullable String irisExtractionFormat,
			@RequestParam(name = FACE_EXTRACTION_FORMAT, required = false) @Nullable String faceExtractionFormat)
			throws IdRepoAppException {
		try {
			return new ResponseEntity<>(draftService.getDraft(registrationId,
					buildExtractionFormatMap(fingerExtractionFormat, irisExtractionFormat, faceExtractionFormat)),
					HttpStatus.OK);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.GET_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "getDraft", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.GET_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, "Publish draft requested");
		}
	}
	
	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PreAuthorize("hasAnyRole(@authorizedRoles.getPutdraftextractbiometricsregistrationId())")
	@PutMapping(path = "/extractbiometrics/{registrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> extractBiometrics(@PathVariable String registrationId,
			@RequestParam(name = FINGER_EXTRACTION_FORMAT, required = false) @Nullable String fingerExtractionFormat,
			@RequestParam(name = IRIS_EXTRACTION_FORMAT, required = false) @Nullable String irisExtractionFormat,
			@RequestParam(name = FACE_EXTRACTION_FORMAT, required = false) @Nullable String faceExtractionFormat) throws IdRepoAppException {
		try {
			return new ResponseEntity<>(draftService.extractBiometrics(registrationId,
					buildExtractionFormatMap(fingerExtractionFormat, irisExtractionFormat, faceExtractionFormat)),
					HttpStatus.OK);
		} catch (IdRepoAppException e) {
			auditHelper.auditError(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.EXTRACT_BIOMETRICS_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, e);
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_DRAFT_CONTROLLER, "extractBiometrics", e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		} finally {
			auditHelper.audit(AuditModules.ID_REPO_CORE_SERVICE, AuditEvents.EXTRACT_BIOMETRICS_DRAFT_REQUEST_RESPONSE, registrationId,
					IdType.ID, "Extract Biometrics draft requested");
		}
	}

	private Map<String, String> buildExtractionFormatMap(String fingerExtractionFormat, String irisExtractionFormat,
			String faceExtractionFormat) {
		Map<String, String> extractionFormats = new HashMap<>();
		if(Objects.nonNull(fingerExtractionFormat)) {
			extractionFormats.put(FINGER_EXTRACTION_FORMAT, fingerExtractionFormat);
		}
		if(Objects.nonNull(irisExtractionFormat)) {
			extractionFormats.put(IRIS_EXTRACTION_FORMAT, irisExtractionFormat);
		}
		if(Objects.nonNull(faceExtractionFormat)) {
			extractionFormats.put(FACE_EXTRACTION_FORMAT, faceExtractionFormat);
		}
		extractionFormats.remove(null);
		return extractionFormats;
	}
	
}