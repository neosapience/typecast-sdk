from enum import Enum

from pydantic import BaseModel, Field


class PlanTier(str, Enum):
    """Subscription plan tier."""

    FREE = "free"
    LITE = "lite"
    PLUS = "plus"
    CUSTOM = "custom"


class Credits(BaseModel):
    """Credit usage information."""

    plan_credits: int = Field(description="Total credits provided by the plan")
    used_credits: int = Field(description="Number of credits used")


class Limits(BaseModel):
    """Usage limit information."""

    concurrency_limit: int = Field(
        description="Maximum number of concurrent requests allowed"
    )


class SubscriptionResponse(BaseModel):
    """Response from `GET /v1/users/me/subscription`."""

    plan: PlanTier = Field(description="Current subscription plan tier")
    credits: Credits = Field(description="Credit usage information")
    limits: Limits = Field(description="Usage limit information")
