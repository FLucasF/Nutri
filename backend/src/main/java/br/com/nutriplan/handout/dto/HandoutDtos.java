package br.com.nutriplan.handout.dto;

import br.com.nutriplan.handout.domain.Handout;
import br.com.nutriplan.handout.domain.PlanHandout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class HandoutDtos {

    private HandoutDtos() {
    }

    public record HandoutRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 8000) String body
    ) {}

    public record HandoutResponse(
            Long id,
            String title,
            String body,
            /** A system template is not editable: it serves as a starting point. */
            boolean systemTemplate,
            boolean editable,
            boolean hasImage,
            String imageName
    ) {
        public static HandoutResponse from(Handout o) {
            return new HandoutResponse(o.getId(), o.getTitle(), o.getBody(),
                    o.isSystemTemplate(), !o.isSystemTemplate(),
                    o.hasImage(), o.getImageName());
        }
    }

    /** A request to attach to the plan. The text is copied at the moment of attaching. */
    public record AttachmentRequest(
            /** Source handout. Null when the text is written on the spot. */
            Long handoutId,
            @Size(max = 150) String title,
            @Size(max = 8000) String body
    ) {}

    public record PlanHandoutResponse(
            Long id,
            Long handoutId,
            String title,
            String body,
            Integer order,
            boolean hasImage
    ) {
        public static PlanHandoutResponse from(PlanHandout o) {
            return new PlanHandoutResponse(o.getId(), o.getHandoutId(),
                    o.getTitle(), o.getBody(), o.getOrder(), o.hasImage());
        }
    }

    public record ReorderRequest(@NotNull java.util.List<Long> ids) {}
}
