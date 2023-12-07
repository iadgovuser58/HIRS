package hirs.attestationca.portal.page.controllers;

import hirs.attestationca.persist.entity.manager.CertificateRepository;
import hirs.attestationca.persist.entity.userdefined.Device;
import hirs.attestationca.persist.entity.userdefined.Certificate;
import hirs.attestationca.persist.entity.userdefined.certificate.PlatformCredential;
import hirs.attestationca.portal.page.PageControllerTest;
import hirs.attestationca.portal.page.PageMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.FlashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static hirs.attestationca.portal.page.Page.PLATFORM_CREDENTIALS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests that test the URL End Points of PlatformCredentialsPageController.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PlatformCredentialsPageControllerTest extends PageControllerTest {

    // Base path for the page
    private String pagePath;

    // Repository manager to handle data access between certificate entity and data storage in db
    @Autowired
    private CertificateRepository certificateRepository;

    // Location of test certs
    private static final String REALPCCERT = "platform_credentials/Intel_pc.cer";
    private static final String NONPCCERT = "certificates/fakeIntelIntermediateCA.pem";
    private static final String BADPCCERT = "certificates/badCert.pem";

    // A cert that is an actual PC cert file and should be parsable.
    private MockMultipartFile realPcCertFile;
    // A file that contains a cert that is not an PC Cert. Should be parsable as a general cert,
    // but should (eventually) not be stored as an PC because it isn't one.
    private MockMultipartFile nonPcCertFile;
    // A file that is not a cert at all, and just contains garbage text.
    private MockMultipartFile badCertFile;

    /**
     * Constructor providing the Page's display and routing specification.
     */
    public PlatformCredentialsPageControllerTest() {
        super(PLATFORM_CREDENTIALS);
        pagePath = getPagePath();
    }

    /**
     * Prepares tests.
     * @throws IOException if test resources are not found
     */
    @BeforeAll
    public void prepareTests() throws IOException {

        // create a multi part file for the controller upload
        String[] pathTokens = REALPCCERT.split("/");
        realPcCertFile = new MockMultipartFile("file", pathTokens[1], "",
                new ClassPathResource(REALPCCERT)
                        .getInputStream());

        pathTokens = NONPCCERT.split("/");
        nonPcCertFile = new MockMultipartFile("file", pathTokens[1], "",
                new ClassPathResource(NONPCCERT).getInputStream());

        pathTokens = BADPCCERT.split("/");
        badCertFile = new MockMultipartFile("file", pathTokens[1], "",
                new ClassPathResource(BADPCCERT).getInputStream());

    }

//    /**
//     * Tests uploading a cert that is a Platform Credential, and archiving it.
//     * @throws Exception if an exception occurs
//     */
//    @Test
//    @Rollback
//    public void uploadAndArchiveValidPlatformCert() throws Exception {
//        Certificate cert = uploadTestCert();
//        archiveTestCert(cert);
//    }

    private Certificate uploadTestCert() throws Exception {

        // perform upload. Attach csv file and add HTTP parameters for the baseline name and type.
        MvcResult result = getMockMvc().perform(MockMvcRequestBuilders
                        .multipart(pagePath + "/upload")
                        .file(realPcCertFile))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // verify redirection messages
        FlashMap flashMap = result.getFlashMap();
        PageMessages pageMessages = (PageMessages) flashMap.get("messages");
        assertEquals(1, pageMessages.getSuccess().size());
        assertEquals(0, pageMessages.getError().size());

        // verify the cert was actually stored
        List<Certificate> records =
                certificateRepository.findAll();
        assertEquals(1, records.size());

        // verify the cert is not yet archived
        Certificate cert = records.iterator().next();
        assertFalse(cert.isArchived());

        return cert;
    }

    private void archiveTestCert(final Certificate cert) throws Exception {

        // now, archive the record
        getMockMvc().perform(MockMvcRequestBuilders
                        .post(pagePath + "/delete")
                        .param("id", cert.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        List<Certificate> records =
                certificateRepository.findAll();
        assertEquals(1, records.size());

        assertTrue(records.iterator().next().isArchived());
    }

    /**
     * Tests that uploading a certificate when an identical certificate is archived will cause
     * the existing certificate to be unarchived and updated.
     * @throws Exception if an exception occurs
     */
    @Test
    @Rollback
    public void uploadCausesUnarchive() throws Exception {

        String[] pathTokens = REALPCCERT.split("/");

        //temp
        List<Certificate> records1 = certificateRepository.findAll();
        System.out.println("\n    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: before 1st upload: records size : " + records1.size());
        if(records1.size() > 0) {
            Certificate c1 = records1.iterator().next();
            System.out.println("    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: before 1st upload: cert ID: " + c1.getId());
            System.out.println("    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: before 1st upload: createTime: " + c1.getCreateTime().getTime());
        }
        System.out.println("\n    ------------------------------------------------------------");

        Certificate cert = uploadTestCert();
        archiveTestCert(cert);

        //temp
        List<Certificate> records2 = certificateRepository.findAll();
        System.out.println("\n    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: before 2nd upload: records size : " + records2.size());
        if(records2.size() > 0) {
            Certificate c2 = records2.iterator().next();
            System.out.println("    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: before 2nd upload: cert ID: " + c2.getId());
            System.out.println("    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: before 2nd upload: createTime: " + c2.getCreateTime().getTime());
        }
        System.out.println("\n    ------------------------------------------------------------");

        // upload the same cert again
        MvcResult result = getMockMvc().perform(MockMvcRequestBuilders
                        .multipart(pagePath + "/upload")
                        .file(realPcCertFile))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // verify redirection messages
        FlashMap flashMap = result.getFlashMap();
        PageMessages pageMessages = (PageMessages) flashMap.get("messages");
        assertEquals(1, pageMessages.getSuccess().size());
        assertEquals(0, pageMessages.getError().size());
        assertEquals("Pre-existing certificate found and unarchived ("
                + pathTokens[1] + "): ",
                pageMessages.getSuccess().get(0));

        // verify there is still only one cert in db
        List<Certificate> records = certificateRepository.findAll();
        assertEquals(1, records.size());

        Certificate newCert = records.iterator().next();

        // verify that the cert was unarchived
        assertFalse(newCert.isArchived());

        //temp
        System.out.println("\n    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: after 2nd upload: records size : " + records.size());
        if(records.size() > 0) {
            System.out.println("    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: after 2nd upload: cert ID: " + newCert.getId());
            System.out.println("    XXXXXXXXXXXXXXXXXXXXXXXXX TestClass: after 2nd upload: createTime: " + newCert.getCreateTime().getTime());
        }

        // verify that the createTime was updated
        assertTrue(newCert.getCreateTime().getTime() > cert.getCreateTime().getTime());
    }

//    /**
//     * Tests uploading a cert that is not a Platform Credential, which results in failure.
//     * @throws Exception if an exception occurs
//     */
//    @Test
//    @Rollback
//    public void uploadNonPlatformCert() throws Exception {
//
//        // verify there are initially no certs in db
//        List<Certificate> originalRecords =
//                certificateRepository.findAll();
//        assertEquals(0, originalRecords.size());
//
//        // perform upload. Attach csv file and add HTTP parameters for the baseline name and type.
//        MvcResult result = getMockMvc().perform(MockMvcRequestBuilders
//                        .multipart(pagePath + "/upload")
//                        .file(nonPcCertFile))
//                .andExpect(status().is3xxRedirection())
//                .andReturn();
//
//        // verify redirection messages
//        FlashMap flashMap = result.getFlashMap();
//        PageMessages pageMessages = (PageMessages) flashMap.get("messages");
//        assertEquals(0, pageMessages.getSuccess().size());
//        assertEquals(1, pageMessages.getError().size());
//
//        // verify the cert was not actually stored
//        List<Certificate> records =
//                certificateRepository.findAll();
//        assertEquals(0, records.size());
//    }
//
//    /**
//     * Tests that uploading something that is not a cert at all results in an error returned
//     * to the web client.
//     * @throws Exception an exception occurs
//     */
//    @Test
//    public void uploadBadPlatformCert() throws Exception {
//        // perform upload. Attach csv file and add HTTP parameters for the baseline name and type.
//        MvcResult result = getMockMvc().perform(MockMvcRequestBuilders
//                        .multipart(pagePath + "/upload")
//                        .file(badCertFile))
//                .andExpect(status().is3xxRedirection())
//                .andReturn();
//
//        // verify redirection messages
//        FlashMap flashMap = result.getFlashMap();
//        PageMessages pageMessages = (PageMessages) flashMap.get("messages");
//        assertEquals(1, pageMessages.getError().size());
//        assertEquals(0, pageMessages.getSuccess().size());
//
//        // verify the cert was not actually stored
//        List<Certificate> records =
//                certificateRepository.findAll();
//        assertEquals(0, records.size());
//    }
}
