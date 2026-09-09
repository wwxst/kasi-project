package com.kasi.backend.promotion.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionProject;
import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionProjectPersistenceTest extends BaseAuthTest {

    @Autowired
    private PromotionProjectMapper projectMapper;

    @Test
    @DisplayName("推广项目支持新增详情分页修改和物理删除")
    void supportsAdminCrud() {
        PromotionProject project = project("项目A", "a.webp", 20, PromotionProjectStatus.ENABLED);

        assertThat(projectMapper.insert(project)).isEqualTo(1);
        assertThat(project.getId()).isNotNull();
        assertThat(projectMapper.countAll()).isEqualTo(1);
        assertThat(projectMapper.findPage(0, 20))
                .extracting(PromotionProject::getName)
                .containsExactly("项目A");

        PromotionProject stored = projectMapper.findById(project.getId());
        stored.setName("项目A-修改");
        stored.setProjectDocumentUrl("https://example.com/a-updated");
        stored.setStatus(PromotionProjectStatus.DISABLED);
        stored.setSortOrder(10);
        assertThat(projectMapper.update(stored)).isEqualTo(1);

        assertThat(projectMapper.findById(project.getId()))
                .extracting(PromotionProject::getName,
                        PromotionProject::getProjectDocumentUrl,
                        PromotionProject::getStatus,
                        PromotionProject::getSortOrder)
                .containsExactly("项目A-修改", "https://example.com/a-updated",
                        PromotionProjectStatus.DISABLED, 10);

        assertThat(projectMapper.deleteById(project.getId())).isEqualTo(1);
        assertThat(projectMapper.findById(project.getId())).isNull();
    }

    @Test
    @DisplayName("管理端和用户端项目按顺序及主键稳定排列且用户端只读取启用项")
    void returnsStableOrderedAdminAndEnabledLists() {
        projectMapper.insert(project("项目C", "c.webp", 20, PromotionProjectStatus.ENABLED));
        projectMapper.insert(project("项目A", "a.webp", 10, PromotionProjectStatus.ENABLED));
        projectMapper.insert(project("项目B", "b.webp", 10, PromotionProjectStatus.DISABLED));
        projectMapper.insert(project("项目D", "d.webp", 10, PromotionProjectStatus.ENABLED));

        assertThat(projectMapper.findPage(0, 20))
                .extracting(PromotionProject::getName)
                .containsExactly("项目A", "项目B", "项目D", "项目C");
        assertThat(projectMapper.findEnabled())
                .extracting(PromotionProject::getName)
                .containsExactly("项目A", "项目D", "项目C");
    }

    private static PromotionProject project(String name, String fileName, int sortOrder,
                                             PromotionProjectStatus status) {
        PromotionProject project = new PromotionProject();
        project.setName(name);
        project.setCoverImageUrl("/uploads/promotion-projects/" + fileName);
        project.setProjectDocumentUrl("https://example.com/" + fileName);
        project.setStatus(status);
        project.setSortOrder(sortOrder);
        return project;
    }
}
